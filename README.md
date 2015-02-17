# hidashhi-cordova
Cordova plugin supporting HidashHi platform

## Intruduction

This plugin enables [HidasHi API](https://github.com/hidashhi/api-doc) on
mobile devices (supported by Android only in current version).

## Installation
Install Cordova:
```
npm install -g cordova
```

Create a new Cordova project:
```
cordova create <name>
cd <name>
cordova platform add android
```
Add the plugin:
```
cordova plugin add https://github.com/hidashhi/hidashhi-cordova.git
```

## Getting started

Once the plugin is installed, you can use the HidashHi JavaScript API as
described in [API documentation](https://github.com/hidashhi/api-doc).

### Ending the call
In order to end the call you must call the following plugin method:
```
cordova.plugins.hidashhi.endCall();
```
This method disconnects from the room and cleans up UI and internal
WebRTC session resources.

### Transparent webview

When the call is started, plugin creates a view to show participant's
video. This view is located behind Cordova webview. In order to see the
video view, you must make the webview transparent. Add following code
to your `CordovaActivity` class successor:

```
public class CordovaApp extends CordovaActivity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        super.init();
        loadUrl(launchUrl);

        // Add 2 lines below:
        super.appView.setBackgroundColor(0x00000000);
        super.appView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
    }
}
```
