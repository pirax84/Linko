package app.linko;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Service de lecture : garde l'appli vivante écran éteint et expose les boutons
 * (notification, écran verrouillé, volant, Bluetooth) via une MediaSession.
 * Le son lui-même est lu par la page web ; ce service ne fait que le "télécommander".
 */
public class PlayerService extends Service {
    static final String CH = "linko_player";
    static final int ID = 7;
    static volatile boolean active = false;
    static PlayerService inst;

    private MediaSession ms;
    private String title = "Linko", artist = "", thumb = "";
    private boolean playing = false;
    private Bitmap art;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        inst = this;
        ms = new MediaSession(this, "Linko");
        ms.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { MainActivity.sendCmd("play"); }
            @Override public void onPause() { MainActivity.sendCmd("pause"); }
            @Override public void onSkipToNext() { MainActivity.sendCmd("next"); }
            @Override public void onSkipToPrevious() { MainActivity.sendCmd("prev"); }
            @Override public void onStop() { MainActivity.sendCmd("stop"); }
        });
        ms.setActive(true);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CH, "Lecture Linko", NotificationManager.IMPORTANCE_LOW);
            c.setShowBadge(false);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    @Override public int onStartCommand(Intent in, int flags, int startId) {
        String a = in == null ? null : in.getAction();
        if (a != null) {
            if (a.equals("app.linko.PREV")) MainActivity.sendCmd("prev");
            else if (a.equals("app.linko.NEXT")) MainActivity.sendCmd("next");
            else if (a.equals("app.linko.TOGGLE")) MainActivity.sendCmd(playing ? "pause" : "play");
            else if (a.equals("app.linko.STOP")) { MainActivity.sendCmd("stop"); stopSelf(); return START_NOT_STICKY; }
            else if (a.equals("app.linko.UPDATE")) {
                title = in.getStringExtra("t"); artist = in.getStringExtra("a");
                String th = in.getStringExtra("i"); playing = in.getBooleanExtra("p", false);
                if (th != null && !th.equals(thumb)) { thumb = th; art = null; loadArt(th); }
            }
        }
        refresh();
        return START_NOT_STICKY;
    }

    private void loadArt(final String u) {
        if (u == null || !u.startsWith("http")) return;
        new Thread(new Runnable() { public void run() {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(6000); c.setReadTimeout(6000);
                InputStream is = c.getInputStream();
                Bitmap b = BitmapFactory.decodeStream(is);
                is.close();
                if (b != null && u.equals(thumb)) { art = b; if (inst != null) inst.refresh(); }
            } catch (Exception e) { }
        } }).start();
    }

    private PendingIntent act(String a, int code) {
        Intent i = new Intent(this, PlayerService.class).setAction(a);
        int f = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return Build.VERSION.SDK_INT >= 26 ? PendingIntent.getForegroundService(this, code, i, f) : PendingIntent.getService(this, code, i, f);
    }

    void refresh() {
        MediaMetadata.Builder m = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title == null ? "Linko" : title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist == null ? "" : artist);
        if (art != null) m.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art).putBitmap(MediaMetadata.METADATA_KEY_ART, art);
        ms.setMetadata(m.build());
        long acts = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP;
        ms.setPlaybackState(new PlaybackState.Builder().setActions(acts)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build());
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title == null ? "Linko" : title)
                .setContentText(artist == null ? "" : artist).setContentIntent(pi).setOngoing(playing).setVisibility(Notification.VISIBILITY_PUBLIC);
        if (art != null) b.setLargeIcon(art);
        if (Build.VERSION.SDK_INT >= 23) {
            b.addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_previous), "Précédent", act("app.linko.PREV", 1)).build());
            b.addAction(new Notification.Action.Builder(Icon.createWithResource(this, playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play), playing ? "Pause" : "Lecture", act("app.linko.TOGGLE", 2)).build());
            b.addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_next), "Suivant", act("app.linko.NEXT", 3)).build());
            b.setStyle(new Notification.MediaStyle().setMediaSession(ms.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        }
        Notification n = b.build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(ID, n);
            active = true;
        } catch (Exception e) { /* refus système : la lecture continue en premier plan seulement */ }
    }

    @Override public void onDestroy() {
        active = false; inst = null;
        try { ms.setActive(false); ms.release(); } catch (Exception e) { }
        super.onDestroy();
    }
}
