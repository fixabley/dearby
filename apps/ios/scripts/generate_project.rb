require 'xcodeproj'
Dir.chdir(File.expand_path('..', __dir__))
project = Xcodeproj::Project.new('Dearby.xcodeproj')
app = project.new_target(:application, 'Dearby', :ios, '18.0')
sources = project.main_group.new_group('Sources')
Dir.glob('Sources/**/*.swift').sort.each { |file| app.source_build_phase.add_file_reference(sources.new_file(file)) }
# Release builds must get https domain origins from the build environment (contract "연결 설정").
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
    if target == app
      config.build_settings.merge!({
        'INFOPLIST_FILE' => 'Info.plist',
        'PRODUCT_BUNDLE_IDENTIFIER' => config.name == 'Release' ? 'io.wid.dearby' : 'com.dearby.dearby',
        'ASSETCATALOG_COMPILER_APPICON_NAME' => 'AppIcon',
        'ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME' => '',
        # Provisional: coordinator must check App Store Connect history before upload.
        'MARKETING_VERSION' => '0.1.0', 'CURRENT_PROJECT_VERSION' => '1',
        'INFOPLIST_KEY_CFBundleDisplayName' => 'Dearby',
        'INFOPLIST_KEY_UILaunchScreen_Generation' => 'YES',
        'INFOPLIST_KEY_UIApplicationSceneManifest_Generation' => 'YES',
        # Universal links for shared cards: the host is the last path component of the web origin.
        'DEARBY_WEB_HOST' => '$(DEARBY_WEB_ORIGIN:file)'
      })
      # Passkeys need webcredentials for the RP ID (contract "패스키 로그인") in both builds. Universal links
      # stay Release only, where the web origin is validated; Debug may have no web origin at all.
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
