# Solicitud técnica preparada, pendiente de enviar

Destinatario: soporte del vendedor/fabricante de la pantalla; alternativamente el [contacto oficial de ZJINNOVA](https://zjinnova.com/contact.html), que publica `product@zjinnova.com`. Este texto no se ha enviado. No adjuntar APK ni diagnósticos privados sin decidir expresamente cuáles compartir.

**Subject: Android launcher integration — ZLink 6.1.06 / DINGWEI-A133**

Hello,

I own a T10S/T100 head unit with an Allwinner A133 processor, 2 GB RAM, 1280 × 720 display, and ZLink 6.1.06 (DINGWEI-A133). Its actual Android SDK level is 29. I am developing a custom launcher for my own unit.

Playback controls work with my iPhone through CarPlay. ZLink displays the current music information internally, but its Android MediaSession does not provide the current title, artist, artwork, duration or elapsed time to the launcher. There is no Android music notification. The original Theme/DashMate screen does not display the current CarPlay song, and its desktop-window settings do not offer a compatible ZLink window.

Please provide or confirm the supported Android integration for this exact ZLink version and platform:

1. SDK/AIDL and sample client for the exported service with Binder descriptor `com.zjinnova.zlink.android.core.codec.IPhoneContentPresenter`, including callback registration, lifetime and required permissions.
2. Current CarPlay track title, artist, album, artwork, duration, elapsed time and playback state, independent of the iPhone music app. Please specify timestamps, units, track identity, app identity, reconnect behaviour and artwork limits.
3. Route guidance callbacks and, separately, a supported video Surface and touch interface for presenting the CarPlay map inside a launcher panel. Please distinguish maneuver data from interactive map video.
4. Whether an ordinary separately signed launcher APK can use these interfaces on this firmware, and the required manufacturer-supported permissions/configuration. The intended installation uses the existing installed ZLink app and normal Android APIs without root or firmware replacement.
5. Whether the metadata/HUD callbacks included in this build have a documented external client interface. Please supply the compatible headers/API and redistribution terms for any required SDK components.

If this firmware cannot expose metadata or a video Surface to another launcher, please confirm that limitation and identify any supported firmware/integration option for this exact head unit.

Thank you.
