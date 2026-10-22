package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** TV ao vivo em tela cheia. Cima e baixo trocam de canal, OK mostra o nome, Voltar sai. */
public class PlayerActivity extends Activity {
    static List<Canais.Canal> lista;
    static int atual;

    private ExoPlayer player;
    private SurfaceView tela;
    private FrameLayout raiz;
    private LinearLayout faixa;
    private TextView nome, aviso;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable esconder = () -> faixa.animate().alpha(0f).setDuration(400).start();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Config.portuguese(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (lista == null || lista.isEmpty()) { finish(); return; }

        raiz = new FrameLayout(this);
        raiz.setBackgroundColor(0xFF000000);
        tela = new SurfaceView(this);
        raiz.addView(tela, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        faixa = new LinearLayout(this);
        faixa.setOrientation(LinearLayout.VERTICAL);
        int p = dp(28);
        faixa.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(0xB3000000);
        faixa.setBackground(g);
        nome = texto(22);
        aviso = texto(14);
        aviso.setTextColor(0xB3FFFFFF);
        faixa.addView(nome);
        faixa.addView(aviso);
        FrameLayout.LayoutParams lf = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        lf.leftMargin = dp(48);
        lf.bottomMargin = dp(48);
        raiz.addView(faixa, lf);
        setContentView(raiz);

        player = new ExoPlayer.Builder(this).build();
        player.setVideoSurfaceView(tela);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException e) {
                mostrarFaixa(getString(R.string.channel_down), false);
            }

            @Override
            public void onPlaybackStateChanged(int estado) {
                if (estado == Player.STATE_READY) mostrarFaixa(null, true);
                else if (estado == Player.STATE_BUFFERING) mostrarFaixa(getString(R.string.channel_loading), false);
            }

            @Override
            public void onVideoSizeChanged(VideoSize v) {
                if (v.width == 0 || v.height == 0) return;
                float pv = v.width * v.pixelWidthHeightRatio / v.height;
                int W = raiz.getWidth(), H = raiz.getHeight();
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tela.getLayoutParams();
                if ((float) W / H > pv) { lp.height = H; lp.width = Math.round(H * pv); }
                else { lp.width = W; lp.height = Math.round(W / pv); }
                tela.setLayoutParams(lp);
            }
        });
        tocar();
    }

    private void tocar() {
        Canais.Canal c = lista.get(atual);
        Map<String, String> cab = new HashMap<>(c.cabecalhos);
        String agent = cab.containsKey("User-Agent") ? cab.remove("User-Agent") : "Mozilla/5.0";
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(15000)
                .setUserAgent(agent);
        http.setDefaultRequestProperties(cab);
        player.setMediaSource(new DefaultMediaSourceFactory(http).createMediaSource(MediaItem.fromUri(Uri.parse(c.url))));
        player.prepare();
        player.setPlayWhenReady(true);
        mostrarFaixa(getString(R.string.channel_loading), false);
    }

    private void mostrarFaixa(String texto, boolean somePoucoDepois) {
        nome.setText(lista.get(atual).nome);
        aviso.setVisibility(texto != null ? TextView.VISIBLE : TextView.GONE);
        if (texto != null) aviso.setText(texto);
        h.removeCallbacks(esconder);
        faixa.animate().cancel();
        faixa.setAlpha(1f);
        if (somePoucoDepois) h.postDelayed(esconder, 3500);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE)
            return super.dispatchKeyEvent(e);
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_CHANNEL_UP) {
            atual = (atual + 1) % lista.size();
            tocar();
        } else if (k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            atual = (atual - 1 + lista.size()) % lista.size();
            tocar();
        } else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_INFO) {
            mostrarFaixa(null, true);
        } else if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_BUTTON_B || k == KeyEvent.KEYCODE_ESCAPE) {
            finish();
        }
        return true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) { player.release(); player = null; }
        finish();
    }

    private TextView texto(int sp) {
        TextView t = new TextView(this);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.outfit));
        return t;
    }

    private int dp(float d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }
}
