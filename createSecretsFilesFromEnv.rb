require 'yaml'

cucumber_properties = Hash.new
cucumber_properties['username'] = ENV['OKTA_CUCUMBER_USERNAME']
cucumber_properties['password'] = ENV['OKTA_CUCUMBER_PASSWORD']
cucumber_properties['invalidUsername'] = ENV['OKTA_CUCUMBER_INVALID_USERNAME']
cucumber_properties['invalidPassword'] = ENV['OKTA_CUCUMBER_INVALID_PASSWORD']
cucumber_properties['firstName'] = ENV['OKTA_CUCUMBER_FIRST_NAME']
cucumber_properties['newPassword'] = ENV['OKTA_CUCUMBER_NEW_PASSWORD']

management_sdk_properties = Hash.new
management_sdk_properties['clientId'] = ENV['OKTA_MANAGEMENT_CLIENT_ID']
management_sdk_properties['orgUrl'] = ENV['OKTA_MANAGEMENT_ORG_URL']
management_sdk_properties['token'] = ENV['OKTA_MANAGEMENT_TOKEN']

a18n_properties = Hash.new
a18n_properties['token'] = ENV['OKTA_A18N_TOKEN']

yaml_properties = Hash.new
yaml_properties['cucumber'] = cucumber_properties
yaml_properties['managementSdk'] = management_sdk_properties
yaml_properties['a18n'] = a18n_properties

File.write('app/src/androidTest/resources/e2eCredentials.yaml', yaml_properties.to_yaml)

okta_properties = ''
okta_properties += "issuer=#{ENV['OKTA_IDX_ISSUER']}\n"
okta_properties += "clientId=#{ENV['OKTA_IDX_CLIENT_ID']}\n"
okta_properties += "redirectUri=#{ENV['OKTA_IDX_REDIRECT_URI']}\n"
File.write('okta.properties', okta_properties)
