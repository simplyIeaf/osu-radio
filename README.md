<p align="center">
  <img width="250" alt="osu!radio logo" src="assets/IMG_1501.png">
</p>

# osu!radio
Android music player that lets you play osu! beatmap songs

## Features
• Mods (basically the music player includes mods that make changes to songs like Nightcore)\
• Playback controls (skip, shuffle etc)\
• Playlists (lets you make playlists, put songs inside them & play them)\
• Automatic updater (prompts you to update to vx.x.x inside the app when a new version is released in the repository)\
more features are coming soon, so stay tuned!

## Setup
### osu!droid
1. Download & Install [osu!droid](https://github.com/osudroid/osu-droid) if you haven't already
2. Open the game (allow the permissions asked), once everything is loaded, press the button on the right side & download beatmaps your choice
3. Open osu!radio, wait till everything loads & the downloaded beatmap(s) should be inside if osu!radio
### Manual
1. Go to the official osu! website, press beatmaps & then beatmap listing (if your lazy, [here](https://osu.ppy.sh/beatmapsets))
2. Select & download beatmaps your choice (you need to login onto your osu! account to download beatmaps)
3. Open file manager, navigate to the downloaded beatmap (.osz), select it & press **Share** (or share icon depending on the file manager you use)
4. Select osu!radio, wait till everything loads & the downloaded beatmap(s) should be inside of osu!radio
> [!NOTE]
> Multi-selecting ths beatmaps & pressing Share **might** not work

## Screenshots
<img src="https://github.com/simplyIeaf/osu-radio/blob/main/assets/Screenshot_20260627-190737_osu!radio.jpg" width="150" height="275"/>
<img src="https://github.com/simplyIeaf/osu-radio/blob/main/assets/Screenshot_20260627-190745_osu!radio.jpg" width="150" height="275"/>

## Building
Requirements: Linux machine (make sure JDK17 & Android SDK is installed)\
Clone the repository:
```
git clone https://github.com/simplyIeaf/osu-radio.git
```
Go inside the cloned repository:
```
cd osu-radio
```
Give **gradlew** permission & build:
```
chmod +x ./gradlew
./gradlew assembleDebug
```

## License
osu!radio is licensed under the [MIT](https://opensource.org/license/mit) License. Please see the [LICENSE](https://github.com/simplyIeaf/osu-radio/blob/main/LICENSE) file for more information.

