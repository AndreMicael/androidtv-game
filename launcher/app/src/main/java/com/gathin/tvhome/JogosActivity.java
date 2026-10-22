package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.res.ResourcesCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Aba de jogos: uma prateleira por sistema, cada jogo um cartucho com a faixa na cor do console
 * e, quando o jogos.json traz, a capa ao fundo.
 */
public class JogosActivity extends Activity {

    private static final String PREFS = "jogos";
    private static final String RECENTES = "recentes";
    private static final int MAX_RECENTES = 6;

    private final java.util.concurrent.ExecutorService capas = java.util.concurrent.Executors.newSingleThreadExecutor();
    private Typeface outfit;
    /** Nome do jogo sendo movido; enquanto não for null, o canto da tela mostra o carregando. */
    private String movendo;
    private float density;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Config.portuguese(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        outfit = ResourcesCompat.getFont(this, R.font.outfit);
        Jogos.criarPastas(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Jogos.noPpsspp = false;
        montar();   // de novo a cada volta: um jogo recém-enviado pelo FTP aparece sem reiniciar
    }

    private void montar() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Config.load(this).backgroundColor);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(56), dp(30), dp(56), dp(40));
        scroll.addView(col);

        List<Jogos.Jogo> todos = Jogos.listar(this);

        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.BOTTOM);
        TextView titulo = texto(getString(R.string.games), 40, 300, 0xFFFFFFFF);
        topo.addView(titulo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.END);
        info.addView(texto(getResources().getQuantityString(R.plurals.games_count, todos.size(), todos.size()), 15, 400, 0x99FFFFFF));
        TextView espaco = texto(espacoLivre(), 13, 400, 0x73FFFFFF);
        espaco.setPadding(0, dp(6), 0, 0);
        info.addView(espaco);
        topo.addView(info);
        // Mesmo visual das pílulas da tela inicial; relê as pastas (pendrive recém-ligado aparece).
        TextView atualizar = texto(getString(R.string.games_refresh), 15, 500, 0);
        atualizar.setTextColor(getColorStateList(R.drawable.pill_text));
        atualizar.setBackgroundResource(R.drawable.pill_bg);
        atualizar.setGravity(Gravity.CENTER);
        atualizar.setPadding(dp(20), 0, dp(20), 0);
        atualizar.setFocusable(true);
        atualizar.setClickable(true);
        atualizar.setOnClickListener(v -> montar());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        alp.setMarginStart(dp(20));
        topo.addView(atualizar, alp);
        col.addView(topo);
        if (!todos.isEmpty()) {
            TextView dica = texto(getString(R.string.games_hold_hint), 13, 400, 0x73FFFFFF);
            dica.setPadding(0, dp(8), 0, 0);
            col.addView(dica);
        }

        View primeiro = null;
        List<Jogos.Jogo> recentes = recentes(todos);
        if (!recentes.isEmpty()) {
            LinearLayout fila = prateleira(col, getString(R.string.games_recent));
            for (Jogos.Jogo j : recentes) {
                View t = cartucho(j);
                fila.addView(t);
                if (primeiro == null) primeiro = t;
            }
        }
        for (Jogos.Sistema s : Jogos.Sistema.values()) {
            LinearLayout fila = null;
            for (Jogos.Jogo j : todos) {
                if (j.sistema != s) continue;
                if (fila == null) fila = prateleira(col, s.nome);
                View t = cartucho(j);
                fila.addView(t);
                if (primeiro == null) primeiro = t;
            }
        }

        if (todos.isEmpty()) {
            TextView dica = texto(getString(R.string.games_folder), 15, 400, 0x99FFFFFF);
            dica.setPadding(0, dp(28), 0, 0);
            col.addView(dica);
        }

        FrameLayout tela = new FrameLayout(this);
        tela.addView(scroll);
        if (movendo != null) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
            lp.setMargins(0, 0, dp(32), dp(24));
            tela.addView(carregando(movendo), lp);
        }
        setContentView(tela);
        if (primeiro != null) primeiro.requestFocus();
    }

    /** Título do sistema e a fileira rolável de cartuchos embaixo dele. */
    private LinearLayout prateleira(LinearLayout col, String nome) {
        TextView t = texto(nome, 18, 500, 0xD9FFFFFF);
        t.setPadding(0, dp(26), 0, dp(10));
        col.addView(t);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.setPadding(0, dp(6), 0, dp(6));
        LinearLayout fila = new LinearLayout(this);
        fila.setClipChildren(false);
        hs.addView(fila);
        col.addView(hs);
        return fila;
    }

    /** Cartucho: capa ao fundo (se houver), faixa colorida no topo, nome grande, ano e jogadores. */
    private View cartucho(Jogos.Jogo j) {
        FrameLayout card = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(232), dp(132));
        lp.setMarginEnd(dp(18));
        card.setLayoutParams(lp);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(fundo(j.sistema.cor, false));
        card.setClipToOutline(true);

        if (j.capa != null && j.capa.isFile()) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            View sombra = new View(this);   // escurece embaixo para o nome continuar legível sobre qualquer capa
            sombra.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xE6000000}));
            card.addView(sombra, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            capas.execute(() -> {
                android.graphics.Bitmap b = miniatura(j.capa, dp(232));
                if (b != null) runOnUiThread(() -> img.setImageBitmap(b));
            });
        }

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setGravity(Gravity.BOTTOM);
        txt.setPadding(dp(18), dp(20), dp(18), dp(16));
        TextView nome = texto(j.nome, 19, 500, 0xFFFFFFFF);
        nome.setMaxLines(2);
        nome.setEllipsize(android.text.TextUtils.TruncateAt.END);
        txt.addView(nome);
        String linha = (j.ano > 0 ? j.ano + "   " : "") + j.jogadores;
        if (!linha.trim().isEmpty()) {
            TextView sub = texto(linha.trim(), 13, 400, 0xB3FFFFFF);
            sub.setPadding(0, dp(4), 0, 0);
            sub.setSingleLine(true);
            txt.addView(sub);
        }
        card.addView(txt, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        card.setOnFocusChangeListener((v, f) -> {
            v.setBackground(fundo(j.sistema.cor, f));
            v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(140).start();
            if (f) v.getParent().requestChildFocus(v, v);
        });
        card.setOnClickListener(v -> abrir(j));
        card.setOnLongClickListener(v -> {
            opcoes(j);
            return true;
        });
        return card;
    }

    private static android.graphics.Bitmap miniatura(java.io.File f, int largura) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= largura) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Corpo escuro com a faixa da cor do sistema; no foco, o corpo clareia e ganha contorno na mesma cor. */
    private LayerDrawable fundo(int cor, boolean foco) {
        float r = dp(14);
        GradientDrawable corpo = new GradientDrawable();
        corpo.setCornerRadius(r);
        corpo.setColor(foco ? 0xFF24262C : 0xFF16171B);
        if (foco) corpo.setStroke(dp(2), cor);
        GradientDrawable faixa = new GradientDrawable();
        faixa.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        faixa.setColor(cor);
        LayerDrawable ld = new LayerDrawable(new android.graphics.drawable.Drawable[]{corpo, faixa});
        ld.setLayerHeight(1, dp(6));
        ld.setLayerGravity(1, Gravity.TOP);
        return ld;
    }

    private void abrir(Jogos.Jogo j) {
        lembrar(j);
        new Thread(() -> {
            Memoria.liberar(this);
            runOnUiThread(() -> {
                if (!Jogos.jogar(this, j)) Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    /** Spinner pequeno e o nome do jogo, no canto de baixo. */
    private View carregando(String nome) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ProgressBar pb = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0x99FFFFFF));
        box.addView(pb, new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView t = texto(getString(R.string.games_moving_name, nome), 13, 400, 0x99FFFFFF);
        t.setPadding(dp(10), 0, 0, 0);
        box.addView(t);
        return box;
    }

    /** "TV: 2,1 GB livres", e o pendrive ao lado quando houver um. */
    private String espacoLivre() {
        String s = getString(R.string.games_space_tv,
                android.text.format.Formatter.formatShortFileSize(this, Jogos.pastaJogos().getUsableSpace()));
        List<File> usb = Jogos.pendrives(this);
        if (!usb.isEmpty()) {
            s += "     " + getString(R.string.games_space_usb,
                    android.text.format.Formatter.formatShortFileSize(this, usb.get(0).getUsableSpace()));
        }
        return s;
    }

    /** Segurar OK: mover o jogo entre a TV e o pendrive, ou apagar. */
    private void opcoes(Jogos.Jogo j) {
        if (movendo != null) return;   // um por vez: o carregando do canto já está mostrando o atual
        File pendrive = Jogos.pendrive(this);
        boolean naTv = Jogos.naTv(j);
        List<String> itens = new ArrayList<>();
        if (pendrive != null) itens.add(getString(naTv ? R.string.games_move_to_usb : R.string.games_move_to_tv));
        itens.add(getString(R.string.games_delete));
        new android.app.AlertDialog.Builder(this)
                .setTitle(j.nome)
                .setItems(itens.toArray(new String[0]), (d, w) -> {
                    if (pendrive != null && w == 0) mover(j, naTv ? pendrive : Jogos.pastaJogos());
                    else confirmarApagar(j);
                })
                .show();
    }

    private void mover(Jogos.Jogo j, File destino) {
        movendo = j.nome;
        montar();
        new Thread(() -> {
            int r = Jogos.mover(j, destino);
            runOnUiThread(() -> {
                movendo = null;
                Toast.makeText(this, r == Jogos.OK ? R.string.games_moved
                        : r == Jogos.SEM_ESPACO ? R.string.games_no_space : R.string.games_move_failed,
                        Toast.LENGTH_LONG).show();
                montar();
            });
        }).start();
    }

    /** Apagar não tem volta: pergunta antes, com Cancelar como botão em foco. */
    private void confirmarApagar(Jogos.Jogo j) {
        android.app.AlertDialog d = new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.games_delete_title, j.nome))
                .setMessage(R.string.games_delete_message)
                .setNegativeButton(R.string.games_cancel, null)
                .setPositiveButton(R.string.games_delete, (x, w) -> {
                    Toast.makeText(this, Jogos.apagar(j) ? R.string.games_deleted : R.string.games_delete_failed,
                            Toast.LENGTH_LONG).show();
                    montar();
                })
                .create();
        d.setOnShowListener(x -> d.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).requestFocus());
        d.show();
    }

    private void lembrar(Jogos.Jogo j) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        StringBuilder b = new StringBuilder(j.chave());
        int n = 1;
        for (String a : p.getString(RECENTES, "").split("[|]")) {
            if (a.isEmpty() || a.equals(j.chave()) || n >= MAX_RECENTES) continue;
            b.append('|').append(a);
            n++;
        }
        p.edit().putString(RECENTES, b.toString()).apply();
    }

    /** Só os recentes que ainda estão na pasta, na ordem em que foram jogados. */
    private List<Jogos.Jogo> recentes(List<Jogos.Jogo> todos) {
        List<Jogos.Jogo> out = new ArrayList<>();
        for (String a : getSharedPreferences(PREFS, MODE_PRIVATE).getString(RECENTES, "").split("[|]")) {
            for (Jogos.Jogo j : todos) if (j.chave().equals(a)) out.add(j);
        }
        return out;
    }

    private TextView texto(String s, float sp, int peso, int cor) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(cor);
        t.setTypeface(outfit);
        t.setFontVariationSettings("'wght' " + peso);
        t.setIncludeFontPadding(false);
        return t;
    }

    @Override
    protected void onDestroy() {
        capas.shutdownNow();
        super.onDestroy();
    }

    private int dp(float d) {
        return Math.round(d * density);
    }
}
