require 'xcodeproj'
Dir.chdir(File.expand_path('..', __dir__))
project = Xcodeproj::Project.new('Dearby.xcodeproj')
app = project.new_target(:application, 'Dearby', :ios, '18.0')
tests = project.new_target(:unit_test_bundle, 'DearbyTests', :ios, '18.0')
tests.add_dependency(app)
ui_tests = project.new_target(:ui_test_bundle, 'DearbyUITests', :ios, '18.0')
ui_tests.add_dependency(app)
[['Sources', app], ['unitTests', tests], ['uiTests', ui_tests]].each do |folder, target|
  group = project.main_group.new_group(folder)
  Dir.glob("#{folder}/**/*.swift").sort.each { |file| target.source_build_phase.add_file_reference(group.new_file(file)) }
end
resources = project.main_group.new_group('Resources')
app.resources_build_phase.add_file_reference(resources.new_file('Resources/Assets.xcassets'))
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
        'ASSETCATALOG_COMPILER_APPICON_NAME' => 'AppIcon',
        'ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME' => '',
        # Provisional: coordinator must check App Store Connect history before upload.
        'MARKETING_VERSION' => '0.1.0', 'CURRENT_PROJECT_VERSION' => '1',
        'INFOPLIST_KEY_CFBundleDisplayName' => 'Dearby',
        'INFOPLIST_KEY_UILaunchScreen_Generation' => 'YES',
        'INFOPLIST_KEY_UIApplicationSceneManifest_Generation' => 'YES',
        'INFOPLIST_KEY_NSCameraUsageDescription' => '명함 QR 코드를 읽기 위해 카메라를 사용합니다.',
        'INFOPLIST_KEY_NSPhotoLibraryAddUsageDescription' => '선택한 명함의 QR 이미지를 사진에 저장합니다.',
        'INFOPLIST_KEY_DearbyAPIURL' => '$(DEARBY_API_URL)',
        'INFOPLIST_KEY_DearbyShareURL' => '$(DEARBY_SHARE_URL)',
        'DEARBY_API_URL' => '', 'DEARBY_SHARE_URL' => ''
      })
      if config.name == 'Debug'
        config.build_settings['SWIFT_ACTIVE_COMPILATION_CONDITIONS'] = 'DEBUG'
        config.build_settings['INFOPLIST_FILE'] = 'Info-Debug.plist'
        config.build_settings['INFOPLIST_KEY_NSAppTransportSecurity_NSAllowsLocalNetworking'] = 'YES'
      end
    elsif target == ui_tests
      config.build_settings['TEST_TARGET_NAME'] = 'Dearby'
    else
      config.build_settings['TEST_HOST'] = '$(BUILT_PRODUCTS_DIR)/Dearby.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/Dearby'
      config.build_settings['BUNDLE_LOADER'] = '$(TEST_HOST)'
    end
  end
end
# The second pass hashes dependency proxies after their target IDs stabilize.
2.times { project.predictabilize_uuids }
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(app)
scheme.add_test_target(tests)
scheme.add_test_target(ui_tests)
scheme.set_launch_target(app)
scheme.save_as(project.path, 'Dearby', true)
