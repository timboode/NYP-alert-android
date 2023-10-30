package com.red.alert.logic.feedback.sound;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.PowerManager;
import android.util.Log;

import com.red.alert.R;
import com.red.alert.config.Logging;
import com.red.alert.config.Sound;
import com.red.alert.logic.alerts.AlertTypes;
import com.red.alert.logic.feedback.VibrationLogic;
import com.red.alert.utils.caching.Singleton;
import com.red.alert.utils.formatting.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class SoundLogic {
    static List<Player> mPlayers;

    public static boolean shouldPlayAlertSound(String alertType, Context context) {
        // No type?
        if (StringUtils.stringIsNullOrEmpty(alertType)) {
            return false;
        }

        // No sound for system message
        if (alertType.equals(AlertTypes.SYSTEM)) {
            return false;
        }

        // Get the audio manager
        AudioManager audioManager = (AudioManager) context.getSystemService(context.AUDIO_SERVICE);

        // Phone is on silent?
        if (audioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            // Override silent mode not allowed?
            if (!shouldOverrideSilentMode(alertType, context)) {
                return false;
            }
        }

        // Play sound
        return true;
    }

    public static int getSoundStreamType(Context context) {
        // Get the audio manager
        AudioManager audioManager = (AudioManager) context.getSystemService(context.AUDIO_SERVICE);

        // In call?
        if (audioManager.getMode() == AudioManager.MODE_IN_CALL) {
            // Play in tiny speaker
            // I think we disabled this because it's played in that speaker already by default
            // return AudioManager.STREAM_VOICE_CALL;
        }

        // Return default stream type
        return Sound.STREAM_TYPE;
    }

    public static Uri getAlertSound(String alertType, String overrideSound, Context context) {
        // Override sound selection?
        if (overrideSound != null) {
            // Convert to URI and return it
            return getSoundURI(overrideSound, context);
        }

        // Get selected sound
        String soundPreference = getSoundPreference(alertType, context);

        // Get sound file path
        String selectedSound = Singleton.getSharedPreferences(context).getString(soundPreference, getDefaultSound(alertType, context));

        // No sound selected?
        if (StringUtils.stringIsNullOrEmpty(selectedSound)) {
            // Nothing to play
            return null;
        }

        // Get path to sound resource
        return getSoundURI(selectedSound, context);
    }

    static Uri getSoundURI(String uri, Context context) {
        // No URI? Nothing to do here
        if (StringUtils.stringIsNullOrEmpty(uri)) {
            return null;
        }

        // Support for old alarms (without the scheme identifier)
        if (!uri.contains(Sound.SCHEME_URI_IDENTIFIER)) {
            return getAppSoundByResourceName(uri, context);
        }

        // Scheme without an actual file?
        if (uri.equals(Sound.APP_SOUND_PREFIX)) {
            return null;
        }

        // com.red.alert:// scheme support
        String soundPrefix = Sound.APP_SOUND_PREFIX;

        // Does it start with this prefix?
        if (uri.startsWith(soundPrefix)) {
            // Extract sound resource name without scheme
            uri = uri.substring(uri.indexOf(soundPrefix) + soundPrefix.length());

            // Convert to resource URI
            return getAppSoundByResourceName(uri, context);
        }

        // Try to parse the URI
        return Uri.parse(uri);
    }

    public static Uri getAppSoundByResourceName(String resourceName, Context context) {
        // Convert to resource ID
        int resourceID = context.getResources().getIdentifier("raw/" + resourceName, "raw", context.getPackageName());

        // Finally, get path to sound file
        Uri alarmSoundURI = Uri.parse("android.resource://" + context.getPackageName() + "/" + resourceID);

        // Return sound URI
        return alarmSoundURI;
    }

    static String getDefaultSound(String alertType, Context context) {
        // By default, regular sound
        String soundDefault = context.getString(R.string.defaultSound);

        // Secondary alert?
        if (alertType.equals(AlertTypes.SECONDARY)) {
            // Set new value
            soundDefault = context.getString(R.string.defaultSecondarySound);
        }

        // Return default sound
        return soundDefault;
    }

    static String getSoundPreference(String alertType, Context context) {
        // By default, primary preference
        String soundPreference = context.getString(R.string.soundPref);

        // Secondary alert?
        if (alertType.equals(AlertTypes.SECONDARY)) {
            // Set new pref
            soundPreference = context.getString(R.string.secondarySoundPref);
        }

        // Return default pref
        return soundPreference;
    }

    public static boolean shouldOverrideSilentMode(String alertType, Context context) {
        // Testing?
        if (alertType.equals(AlertTypes.TEST_SOUND) || alertType.equals(AlertTypes.TEST_SECONDARY_SOUND)) {
            return true;
        }

        // Get enabled / disabled setting
        boolean overrideSilentMode = Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.silentOverridePref), true);

        // Secondary alert?
        if (alertType.equals(AlertTypes.SECONDARY)
                || alertType.equals(AlertTypes.TEST_SECONDARY_SOUND)) {
            overrideSilentMode = Singleton.getSharedPreferences(context).getBoolean(context.getString(R.string.secondarySilentOverridePref), true);
        }

        // Return setting value
        return overrideSilentMode;
    }

    static boolean isSoundTypeCurrentlyPlaying(String soundType) {
        // Got a player?
        if (mPlayers != null) {
            // Traverse players
            for (Player player : mPlayers) {
                try {
                    // Still playing?
                    if (player.isPlaying()) {
                        // Check sound type for match
                        if (player.getSoundType().equals(soundType)) {
                            return true;
                        }
                    }
                } catch (Exception exc) {
                    // Ignore exceptions and return false
                }
            }
        }

        // Sound type not currently playing
        return false;
    }

    public static void playSound(String alertType, String alertSound, Context context) {
        // Should we play it?
        if (!shouldPlayAlertSound(alertType, context)) {
            return;
        }

        // Get path to resource
        Uri alarmSoundURI = getAlertSound(alertType, alertSound, context);

        // Invalid sound URI?
        if (alarmSoundURI == null) {
            return;
        }

        // Avoid playing same sound type currently
        if (isSoundTypeCurrentlyPlaying(alertType)) {
            return;
        }

        // Override volume (also to set the user's chosen volume)
        VolumeLogic.setStreamVolume(alertType, context);

        // Play sound
        playSoundURI(alarmSoundURI, alertType, context);
    }

    public static void stopSound(Context context) {
        // Got a player?
        if (mPlayers != null) {
            // Traverse players
            for (Player player : mPlayers) {
                try {
                    // Still playing?
                    if (player.isPlaying()) {
                        // Stop playing
                        player.stop();

                        // Reset media player
                        player.reset();
                    }

                    // Release resources associated
                    player.release();
                }
                catch (Exception exc) {
                    // Do nothing
                }
            }

            // Clear list
            mPlayers.clear();
        }

        // Stop vibration
        VibrationLogic.stopVibration(context);
    }

    static void playSoundURI(Uri alarmSoundUi, String soundType, Context context) {
        // No URI?
        if (alarmSoundUi == null) {
            return;
        }

        // Create new MediaPlayer
        Player player = new Player();

        // Set sound type
        player.setSoundType(soundType);

        // Wake up processor
        player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);

        // Set stream type
        player.setAudioStreamType(getSoundStreamType(context));

        try {
            // Set URI data source
            player.setDataSource(context, alarmSoundUi);

            // Prepare media player
            player.prepare();

            // Actually start playing
            player.start();
        }
        catch (Exception exc) {
            // Log it
            Log.e(Logging.TAG, "Media player preparation failed", exc);

            // Stop execution
            return;
        }

        // Initialize list
        if (mPlayers == null) {
            mPlayers = new ArrayList<>();
        }

        // Add player to list of players
        mPlayers.add(player);
    }

    /* Declare custom MediaPlayer class to store alert sound type */
    static class Player extends MediaPlayer {
        String mSoundType;

        public String getSoundType() {
            // Return sound type
            return mSoundType;
        }

        public void setSoundType(String soundType) {
            // Store sound type for later
            mSoundType = soundType;
        }
    }
}
