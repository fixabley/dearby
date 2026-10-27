require 'xcodeproj'
Dir.chdir(File.expand_path('..', __dir__))
project = Xcodeproj::Project.new('Dearby.xcodeproj')
app = project.new_target(:application, 'Dearby', :ios, '18.0')
sources = project.main_group.new_group('Sources')
source_refs = Dir.glob('Sources/**/*.swift').sort.to_h { |file| [file, sources.new_file(file)] }
source_refs.each_value { |ref| app.source_build_phase.add_file_reference(ref) }
# 행사 당일 진행 중 표시 (native-v1.md "행사 당일 진행 중 표시" 2026-10-26): the Live Activity is drawn by the `DearbyWidgets` extension
# (`Widgets/`, bundle io.wid.dearby.widgets, App Group group.io.wid.dearby on the extension only); the ActivityAttributes file is compiled into
# both targets. Store signing needs a second App Store profile (DEARBY_RELEASE_PROFILE_WIDGETS, ios-testflight.yml); while a Release
# project is generated without one the extension is left out altogether, so the TestFlight build has no Live Activity but still ships.
include_widgets = ENV['DEARBY_RELEASE_PROFILE'].nil? || !ENV.fetch('DEARBY_RELEASE_PROFILE_WIDGETS', '').empty?
widgets = nil
if include_widgets
  widgets = project.new_target(:app_extension, 'DearbyWidgets', :ios, '18.0')
  widget_group = project.main_group.new_group('Widgets')
  Dir.glob('Widgets/**/*.swift').sort.each { |file| widgets.source_build_phase.add_file_reference(widget_group.new_file(file)) }
  # The 진행 트랙 segment and the color tokens it uses, and the `안내 종료` intent, are shared with the app (contract "표시 디자인·행사 안내 — 2026-10-27").
  %w[Sources/features/liveSession/model/LiveSessionAttributes.swift Sources/features/liveSession/model/EndLiveGuideIntent.swift
     Sources/shared/ui/StageSegment.swift Sources/shared/ui/DearbyStyle.swift].each do |file|
    widgets.source_build_phase.add_file_reference(source_refs.fetch(file))
  end
  widgets.resources_build_phase.add_file_reference(widget_group.new_file('Widgets/Assets.xcassets'))
  app.add_dependency(widgets)
  embed = app.new_copy_files_build_phase('Embed Foundation Extensions')
  embed.symbol_dst_subfolder_spec = :plug_ins
  embed.add_file_reference(widgets.product_reference).settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }
end
# Remote packages: the Google sign-in SDK (contract "구글 로그인 — 2026-10-19 네이티브 SDK로 전환") and Firebase Analytics without the
# advertising identifier (analytics-v1.md 2026-10-25; `FirebaseAnalyticsCore` is 12.0's rename of `FirebaseAnalyticsWithoutAdIdSupport`).
# Firebase is pinned exactly: the generated project and its Package.resolved are not tracked, so every build resolves again.
# The Google Mobile Ads SDK (맞춤 대기열 끝 광고, non-personalised) is pinned exactly for the same reason.
{
  'https://github.com/google/GoogleSignIn-iOS' => [{ 'kind' => 'upToNextMajorVersion', 'minimumVersion' => '10.0.0' }, 'GoogleSignIn'],
  'https://github.com/firebase/firebase-ios-sdk' => [{ 'kind' => 'exactVersion', 'version' => '13.1.1' }, 'FirebaseAnalyticsCore'],
  'https://github.com/googleads/swift-package-manager-google-mobile-ads' => [{ 'kind' => 'exactVersion', 'version' => '13.11.0' }, 'GoogleMobileAds']
}.each do |url, (requirement, product_name)|
  package = project.new(Xcodeproj::Project::Object::XCRemoteSwiftPackageReference)
  package.repositoryURL = url
  package.requirement = requirement
  project.root_object.package_references << package
  product = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
  product.package = package
  product.product_name = product_name
  app.package_product_dependencies << product
  link = project.new(Xcodeproj::Project::Object::PBXBuildFile)
  link.product_ref = product
  app.frameworks_build_phase.files << link
end
# Release builds must get the https API origin from the build environment (contract "연결 설정").
validate = app.new_shell_script_build_phase('Validate connection origins')
validate.shell_script = 'bash "$SRCROOT/scripts/validate_origins.sh"'
validate.always_out_of_date = '1'
app.build_phases.move(validate, 0)
resources = project.main_group.new_group('Resources')
app.resources_build_phase.add_file_reference(resources.new_file('Resources/Assets.xcassets'))
# App-level privacy manifest (required-reason APIs, collected data); the SDKs carry their own.
app.resources_build_phase.add_file_reference(resources.new_file('Resources/PrivacyInfo.xcprivacy'))
# Pretendard copies of shared/assets/fonts/pretendard; also listed in UIAppFonts.
Dir.glob('Resources/Fonts/*.otf').sort.each { |file| app.resources_build_phase.add_file_reference(resources.new_file(file)) }
# Firebase configuration (gitignored; CI writes it from IOS_GOOGLE_SERVICE_INFO_PLIST_BASE64, device builds copy ~/.dearby-deploy's).
# Without it the app builds and 사용 통계 is off (`AppAnalytics.start`).
firebase_config = 'Config/GoogleService-Info.plist'
app.resources_build_phase.add_file_reference(resources.new_file(firebase_config)) if File.exist?(firebase_config)
# 맞춤 대기열 끝 광고 (AdMob, native-v1.md): Debug uses Google's sample app and native test unit. Release takes ADMOB_IOS_APP_ID and
# ADMOB_IOS_QUEUE_NATIVE_UNIT_ID from the environment (CI: the repository variables of those names); without both the unit is empty, the app
# never starts the SDK and the ad page is left out. GADApplicationIdentifier still gets the sample app ID then, because the SDK aborts at launch when it is missing.
# UI flow tests (XCUITest, 2026-10-26 사용자 결정): run by hand on a real iPhone only (README "UI 흐름 테스트"). The target sits in its own
# `DearbyUITests` scheme; the `Dearby` scheme that CI builds and archives (native.yml, ios-testflight.yml) does not contain it.
uitests = project.new_target(:ui_test_bundle, 'DearbyUITests', :ios, '18.0')
uitests.add_dependency(app)
ui_sources = project.main_group.new_group('UITests')
Dir.glob('UITests/**/*.swift').sort.each { |file| uitests.source_build_phase.add_file_reference(ui_sources.new_file(file)) }
# 계획 로직 단위 테스트 (native-v1.md "행사 당일 진행 중 표시", 2026-10-27 사용자 합의): `Tests/` is an XCTest bundle hosted in the app
# (`@testable import Dearby`), in the `Dearby` scheme's test action only — `xcodebuild … build` on that scheme does not compile it;
# `xcodebuild test -scheme Dearby -only-testing:DearbyTests` runs it (README "단위 테스트").
tests = project.new_target(:unit_test_bundle, 'DearbyTests', :ios, '18.0')
tests.add_dependency(app)
test_sources = project.main_group.new_group('Tests')
Dir.glob('Tests/**/*.swift').sort.each { |file| tests.source_build_phase.add_file_reference(test_sources.new_file(file)) }
admob_test_app = 'ca-app-pub-3940256099942544~1458002511'
admob_release = [ENV.fetch('ADMOB_IOS_APP_ID', ''), ENV.fetch('ADMOB_IOS_QUEUE_NATIVE_UNIT_ID', '')]
admob_release = [admob_test_app, ''] if admob_release.any?(&:empty?)
admob = { 'Debug' => [admob_test_app, 'ca-app-pub-3940256099942544/3986624511'], 'Release' => admob_release }
# 광고 끄기 (native-v1.md "맞춤 대기열 끝 광고" 2026-10-26): the queue ad, its consent sheet and the ATT prompt are off unless DEARBY_ADS_ENABLED=true
# (Info.plist `DearbyAdsEnabled`); the code, consent flow and SDK stay in the build. Off by default for the initial test period, in every configuration.
ads_enabled = (ENV['DEARBY_ADS_ENABLED'] == 'true').to_s
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
    # The extension (present only with DEARBY_RELEASE_PROFILE_WIDGETS then, see above) gets its own App Store profile the same way.
    if [app, widgets].include?(target) && config.name == 'Release' && ENV['DEARBY_RELEASE_PROFILE']
      config.build_settings.merge!({
        'CODE_SIGN_STYLE' => 'Manual', 'CODE_SIGN_IDENTITY' => 'Apple Distribution',
        'PROVISIONING_PROFILE_SPECIFIER' => target == app ? ENV['DEARBY_RELEASE_PROFILE'] : ENV['DEARBY_RELEASE_PROFILE_WIDGETS'],
        'DEVELOPMENT_TEAM' => ENV.fetch('DEARBY_TEAM_ID')
      })
    end
    config.build_settings['TEST_TARGET_NAME'] = 'Dearby' if target == uitests
    if target == tests
      config.build_settings.merge!({ 'TEST_HOST' => '$(BUILT_PRODUCTS_DIR)/Dearby.app/Dearby', 'BUNDLE_LOADER' => '$(TEST_HOST)' })
    end
    if target == widgets
      config.build_settings.merge!({
        'INFOPLIST_FILE' => 'Widgets/Info.plist',
        'PRODUCT_BUNDLE_IDENTIFIER' => 'io.wid.dearby.widgets',
        'CODE_SIGN_ENTITLEMENTS' => 'Widgets/DearbyWidgets.entitlements',
        'ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME' => '',
        'ASSETCATALOG_COMPILER_WIDGET_BACKGROUND_COLOR_NAME' => '',
        # App Store validation wants the extension's short version to match the app's; the build number comes from the archive command line.
        'MARKETING_VERSION' => '0.1.0', 'CURRENT_PROJECT_VERSION' => '1',
        'INFOPLIST_KEY_CFBundleDisplayName' => 'Dearby', 'SKIP_INSTALL' => 'YES'
      })
      config.build_settings['SWIFT_ACTIVE_COMPILATION_CONDITIONS'] = 'DEBUG' if config.name == 'Debug'
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
        'GOOGLE_IOS_URL_SCHEME' => ENV.fetch('GOOGLE_IOS_URL_SCHEME', ''),
        'ADMOB_APP_ID' => admob[config.name][0], 'ADMOB_NATIVE_UNIT_ID' => admob[config.name][1], 'DEARBY_ADS_ENABLED' => ads_enabled
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
scheme.add_test_target(tests)
scheme.set_launch_target(app)
scheme.save_as(project.path, 'Dearby', true)
ui_scheme = Xcodeproj::XCScheme.new
ui_scheme.add_build_target(app)
ui_scheme.add_test_target(uitests)
ui_scheme.set_launch_target(app)
ui_scheme.save_as(project.path, 'DearbyUITests', true)
