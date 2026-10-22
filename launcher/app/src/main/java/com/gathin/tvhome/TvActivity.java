package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Canais gratuitos do tv-home, agrupados por categoria. */
public class TvActivity extends Activity {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Config.portuguese(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Canais canais = new Canais(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(48);
        rows.setPadding(pad, dp(28), pad, pad);
        scroll.addView(rows);
        setContentView(scroll);

        if (canais.todos.isEmpty()) {
            TextView vazio = titulo(getString(R.string.no_channels));
            vazio.setAllCaps(false);
            vazio.setLetterSpacing(0f);
            vazio.setTextSize(16);
            rows.addView(vazio);
            return;
        }

        View primeiro = null;
        for (Map.Entry<String, List<Canais.Canal>> e : canais.porCategoria.entrySet()) {
            rows.addView(titulo(e.getKey()));
            List<Canais.Canal> tocaveis = new ArrayList<>();
            for (Canais.Canal c : e.getValue()) if (c.app == null && c.url != null) tocaveis.add(c);
            for (Canais.Canal c : e.getValue()) {
                View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
                ((ImageView) row.findViewById(R.id.icon)).setImageResource(R.drawable.ic_livetv);
                ((TextView) row.findViewById(R.id.title)).setText(c.nome);
                row.findViewById(R.id.dot).setVisibility(View.GONE);
                row.findViewById(R.id.status).setVisibility(View.GONE);
                row.setOnClickListener(v -> abrir(c, tocaveis));
                rows.addView(row);
                if (primeiro == null) primeiro = row;
            }
        }
        if (primeiro != null) primeiro.requestFocus();
        new Thread(canais::atualizarSePreciso).start();
    }

    private void abrir(Canais.Canal c, List<Canais.Canal> tocaveis) {
        if (c.app != null) {
            abrirApp(c);
            return;
        }
        if (c.url == null || tocaveis.isEmpty()) {
            Toast.makeText(this, R.string.channel_down, Toast.LENGTH_SHORT).show();
            return;
        }
        PlayerActivity.lista = tocaveis;
        PlayerActivity.atual = Math.max(0, tocaveis.indexOf(c));
        startActivity(new Intent(this, PlayerActivity.class));
    }

    private void abrirApp(Canais.Canal c) {
        Intent i = null;
        if (c.link != null) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse(c.link)).setPackage(c.app);
            if (getPackageManager().resolveActivity(i, 0) == null) i = null;
        }
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(c.app);
        if (i == null) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private TextView titulo(String s) {
        TextView t = new TextView(this);
        t.setText(s.toUpperCase(java.util.Locale.ROOT));
        t.setTextColor(0x8CFFFFFF);
        t.setTextSize(12);
        t.setLetterSpacing(0.14f);
        t.setPadding(dp(12), dp(18), dp(12), dp(6));
        t.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.outfit));
        return t;
    }

    private int dp(float d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }
}
