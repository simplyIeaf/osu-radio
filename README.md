<p align="center">
  <img width="250" alt="osu!radio logo" src="assets/IMG_1501.png">
</p>

# osu!radio
Open-source music player that lets you play osu! beatmap songs

## Features
• Mods (basically the music player includes mods that make changes to songs like Nightcore)\
• Playback controls (skip, shuffle etc)\
• Playlists (lets you make playlists, put songs inside them & play them)\
• Automatic updater (prompts you to update to vx.x.x inside the app when a new version is released in the repository)\
and many more features!

## Installation
### Android
1. Head over to the [Releases](https://github.com/simplyIeaf/osu-radio/releases) page & download the latest `.apk`
2. Install the APK (the phone will ask you to allow installing apps from unknown sources if you haven't before, allow through settings)
3. Open osu!radio

### Linux
1. Head over to the [Releases](https://github.com/simplyIeaf/osu-radio/releases) page & download the latest `.AppImage`
2. Open a terminal in your download folder & run:
```bash
chmod +x osu-radio-<version>.AppImage
./osu-radio-<version>.AppImage
```
> [!NOTE]
> You can also install `.AppImage` files as apps using [Gear Lever](https://flathub.org/en/apps/it.mijorus.gearlever)

## Setup
### Android
#### Built-in Downloader (Recommended)
1. Download & open osu!radio
2. Press the Download tab
3. Download songs your choice & the downloaded beatmap(s) should be inside of osu!radio in the Songs tab
#### osu!droid
1. Download & Install [osu!droid](https://github.com/osudroid/osu-droid) if you haven't already
2. Open the game (allow the permissions asked), once everything is loaded, press the button on the right side & download beatmaps your choice
3. Open osu!radio, press Settings, press the Synchronization tab & then osu!droid
4. Wait till it's done syncing & the synced beatmap(s) should be inside of osu!radio in the Songs tab
#### Manual
1. Go to the official osu! website, press beatmaps & then beatmap listing (if your lazy, [here](https://osu.ppy.sh/beatmapsets))
2. Select & download beatmaps your choice (you need to login onto your osu! account to download beatmaps)
3. Open file manager, navigate to the downloaded beatmap (.osz), select it & press **Share** (or share icon depending on the file manager you use)
4. Select osu!radio, wait till everything loads & the downloaded beatmap(s) should be inside of osu!radio in the Songs tab
> [!NOTE]
> Multi-selecting the beatmaps & pressing Share **might** not work

### Linux
#### Built-in Downloader (Recommended)
1. Open osu!radio
2. Press the Download tab
3. Download songs your choice & the downloaded beatmap(s) should be inside of osu!radio in the Songs tab
#### Drag & drop
1. Go to the official osu! website & download beatmaps your choice (you need to login onto your osu! account to download beatmaps)
2. Drag the downloaded .osz or .zip & drop it onto the osu!radio window
3. Wait till the import is done & the imported beatmap(s) should be inside of osu!radio in the Songs tab
#### Songs folder
1. Download & unzip the beatmap(s) your choice (a .osz is just a zip file, so you can extract it yourself)
2. Move the beatmap folder(s) into `~/.local/share/osu-radio/Songs/`
3. Restart osu!radio & the beatmap(s) should be inside of osu!radio in the Songs tab

## Screenshots
<img src="https://github.com/simplyIeaf/osu-radio/blob/main/assets/Screenshot_20260627-190737_osu!radio.jpg" width="150" height="275"/>
<img src="https://github.com/simplyIeaf/osu-radio/blob/main/assets/Screenshot_20260627-190745_osu!radio.jpg" width="150" height="275"/>
(outdated)

## License
osu!radio is licensed under the [MIT](https://opensource.org/license/mit) License. Please see the [LICENSE](https://github.com/simplyIeaf/osu-radio/blob/main/LICENSE) file for more information.
