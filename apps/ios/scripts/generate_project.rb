require 'xcodeproj'
Dir.chdir(File.expand_path('..', __dir__))
project = Xcodeproj::Project.new('Dearby.xcodeproj')
app = project.new_target(:application, 'Dearby', :ios, '18.0')
sources = project.main_group.new_group('Sources')
Dir.glob('Sources/**/*.swift').sort.each { |file| app.source_build_phase.add_file_reference(sources.new_file(file)) }
# Google sign-in SDK (contract "구글 로그인 — 2026-10-19 네이티브 SDK로 전환"): the only remote package.
google = project.new(Xcodeproj::Project::Object::XCRemoteSwiftPackageReference)
google.repositoryURL = 'https://github.com/google/GoogleSignIn-iOS'
google.requirement = { 'kind' => 'upToNextMajorVersion', 'minimumVersion' => '10.0.0' }
project.root_object.package_references << google
google_product = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
google_product.package = google
google_product.product_name = 'GoogleSignIn'
app.package_product_dependencies << google_product
google_link = project.new(Xcodeproj::Project::Object::PBXBuildFile)
google_link.product_ref = google_product
app.frameworks_build_phase.files << google_link
# Release builds must get the https API origin from the build environment (contract "연결 설정").
validate = app.new_shell_script_build_phase('Validate connection origins')
validate.shell_script = 'bash "$SRCROOT/scripts/validate_origins.sh"'
validate.always_out_of_date = '1'
app.build_phases.move(validate, 0)
resources = project.main_group.new_group('Resources')
app.resources_build_phase.add_file_reference(resources.new_file('Resources/Assets.xcassets'))
# Pretendard copies of shared/assets/fonts/pretendard; also listed in UIAppFonts.
Dir.glob('Resources/Fonts/*.otf').sort.each { |file| app.resources_build_phase.add_file_reference(resources.new_file(file)) }
project.targets.each do |target|
  target.build_configurations.each do |config|
    config.build_settings.merge!({
      'SWIFT_VERSION' => '6.0', 'GENERATE_INFOPLIST_FILE' => 'YES',
      'PRODUCT_BUNDLE_IDENTIFIER' => "com.dearby.#{target.name.downcase}",
      'CODE_SIGN_STYLE' => 'Automatic', 'TARGETED_DEVICE_FAMILY' => '1,2',
      'IPHONEOS_DEPLOYMENT_TARGET' => '18.0', 'SWIFT_STRICT_CONCURRENCY' => 'complete'
    })
    # Store signing (ios-testflight.yml): the distribution profile applies to the app target only.
    # Passing it on the xcodebuild command line also hits Swift package resource bundles, which reject profiles.
    if target == app && config.name == 'Release' && ENV['DEARBY_RELEASE_PROFILE']
      config.build_settings.merge!({
        'CODE_SIGN_STYLE' => 'Manual', 'CODE_SIGN_IDENTITY' => 'Apple Distribution',
        'PROVISIONING_PROFILE_SPECIFIER' => ENV['DEARBY_RELEASE_PROFILE'], 'DEVELOPMENT_TEAM' => ENV.fetch('DEARBY_TEAM_ID')
      })
    end
    if target == app
      config.build_settings.merge!({
        'INFOPLIST_FILE' => 'Info.plist',
        'PRODUCT_BUNDLE_IDENTIFIER' => 'io.wid.dearby',
        'ASSETCATALOG_COMPILER_APPICON_NAME' => 'AppIcon',
        'ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME' => '',
        # Provisional: coordinator must check App Store Connect history before upload.
        'MARKETING_VERSION' => '0.1.0', 'CURRENT_PROJECT_VERSION' => '1',
        'INFOPLIST_KEY_CFBundleDisplayName' => 'Dearby',
        'INFOPLIST_KEY_UIApplicationSceneManifest_Generation' => 'YES',
        # Google sign-in: the iOS OAuth client ID and its reversed form (com.googleusercontent.apps.<id>) from the build
        # environment; empty hides the button. Local device builds read them from ~/.dearby-deploy/mobile.env (README).
        'GOOGLE_IOS_CLIENT_ID' => ENV.fetch('GOOGLE_IOS_CLIENT_ID', ''),
        'GOOGLE_IOS_URL_SCHEME' => ENV.fetch('GOOGLE_IOS_URL_SCHEME', '')
      })
      # Passkeys need webcredentials for the RP ID (contract "패스키 로그인") in both builds; aps-environment is development in Debug and
      # production in Release (contract "즐겨찾기(하트)·새 모집 푸시 알림": the App ID and the store profile need Push Notifications).
      config.build_settings['CODE_SIGN_ENTITLEMENTS'] = config.name == 'Release' ? 'Dearby.entitlements' : 'Dearby-Debug.entitlements'
      if config.name == 'Debug'
        config.build_settings['SWIFT_ACTIVE_COMPILATION_CONDITIONS'] = 'DEBUG'
        config.build_settings['INFOPLIST_FILE'] = 'Info-Debug.plist'
      end
    end
  end
end
# The second pass hashes dependency proxies after their target IDs stabilize.
2.times { project.predictabilize_uuids }
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(app)
scheme.set_launch_target(app)
scheme.save_as(project.path, 'Dearby', true)
