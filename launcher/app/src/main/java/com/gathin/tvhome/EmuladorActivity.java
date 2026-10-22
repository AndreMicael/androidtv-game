package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.swordfish.libretrodroid.GLRetroView;
import com.swordfish.libretrodroid.GLRetroViewData;
import com.swordfish.libretrodroid.ShaderConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Jogo em tela cheia, com os núcleos empacotados. BACK ou SELECT+START abre o menu. */
public class EmuladorActivity extends ComponentActivity {

    static final String EXTRA_ROM = "ROM";
    static final String EXTRA_SAIR = "SAIR";
    /** Quando o jogo saiu da frente sem ter sido fechado (0 = está na frente ou fechado). */
    private static long pausadoEm;

    /** O jogo foi para o fundo agora há pouco: foi o Home, não uma saída pelo menu. */
    static boolean acabouDeSair() {
        return pausadoEm > 0 && android.os.SystemClock.uptimeMillis() - pausadoEm < 2000;
    }
    static final String EXTRA_CORE = "CORE";

    private GLRetroView retro;
    private String stem;
    private boolean selectDown, startDown, menuAberto;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Config.portuguese(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Clock da CPU estável (quando a TV suporta) e modo jogo da imagem: sem filtros, menos atraso no controle.
        android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
        if (pm != null && pm.isSustainedPerformanceModeSupported()) getWindow().setSustainedPerformanceMode(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) getWindow().setPreferMinimalPostProcessing(true);
        String rom = getIntent().getStringExtra(EXTRA_ROM);
        String core = getIntent().getStringExtra(EXTRA_CORE);
        if (rom == null || core == null) {
            finish();
            return;
        }
        stem = new File(rom).getName();
        int dot = stem.lastIndexOf('.');
        if (dot > 0) stem = stem.substring(0, dot);

        File system = getExternalFilesDir("system");
        File saves = getExternalFilesDir("saves");
        File states = getExternalFilesDir("states");
        if (system != null) system.mkdirs();
        if (saves != null) saves.mkdirs();
        if (states != null) states.mkdirs();

        GLRetroViewData data = new GLRetroViewData(this);
        data.setCoreFilePath(getApplicationInfo().nativeLibraryDir + "/" + core);
        data.setGameFilePath(rom);
        data.setSystemDirectory(system != null ? system.getAbsolutePath() : getFilesDir().getAbsolutePath());
        data.setSavesDirectory(saves != null ? saves.getAbsolutePath() : getFilesDir().getAbsolutePath());
        data.setPreferLowLatencyAudio(true);
        data.setRumbleEventsEnabled(false);
        data.setShader(crtNivel() > 0 ? ShaderConfig.CRT.INSTANCE : ShaderConfig.Default.INSTANCE);
        byte[] sram = ler(arquivoSram());
        if (sram != null) data.setSaveRAMState(sram);

        retro = new GLRetroView(this, data);
        retro.setFocusable(true);
        retro.setFocusableInTouchMode(true);
        getLifecycle().addObserver(retro);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.addView(retro, new FrameLayout.LayoutParams(-1, -1));
        tubo = new TuboView(this);
        root.addView(tubo, new FrameLayout.LayoutParams(-1, -1));
        aplicarCrt(crtNivel());
        setContentView(root);
        retro.requestFocus();
        // Tem estado salvo: pergunta se continua de onde parou (depois que o jogo já começou a rodar).
        if (arquivoEstado().isFile()) root.postDelayed(this::perguntarContinuar, 800);
    }

    private void perguntarContinuar() {
        if (isFinishing() || menuAberto || retro == null) return;
        menuAberto = true;
        retro.setAudioEnabled(false);
        retro.setFrameSpeed(0);
        new AlertDialog.Builder(this)
                .setTitle(R.string.emu_resume_title)
                .setItems(new String[]{getString(R.string.emu_resume), getString(R.string.emu_restart)}, (d, w) -> {
                    fecharMenu();
                    if (w == 0) continuarEstado(10);
                })
                .setOnCancelListener(d -> fecharMenu())
                .show();
    }

    // shortcut: o núcleo pode ainda não ter terminado de carregar o jogo; tenta de novo a cada 300 ms, até 10 vezes.
    private void continuarEstado(int tentativas) {
        byte[] data = ler(arquivoEstado());
        boolean ok = false;
        try {
            ok = data != null && retro.unserializeState(data, false);
        } catch (Exception ignored) {
        }
        if (!ok && tentativas > 0) retro.postDelayed(() -> continuarEstado(tentativas - 1), 300);
        else if (!ok) Toast.makeText(this, R.string.emu_load_failed, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE)
            return super.dispatchKeyEvent(e);
        if (tratarTecla(e)) return true;
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return tratarTecla(event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return tratarTecla(event) || super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (tratarMotion(e)) return true;
        return super.dispatchGenericMotionEvent(e);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        return tratarMotion(e) || super.onGenericMotionEvent(e);
    }

    private boolean tratarTecla(KeyEvent e) {
        if (e == null || retro == null || menuAberto) return false;
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_BUTTON_SELECT) selectDown = down;
        if (k == KeyEvent.KEYCODE_BUTTON_START) startDown = down;
        if (down && (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE
                || (selectDown && startDown))) {
            abrirMenu();
            return true;
        }
        // Home do controle: sair do jogo (perguntando se salva), de volta para a lista de jogos.
        if (k == KeyEvent.KEYCODE_BUTTON_MODE) {
            if (down) perguntarSair();
            return true;
        }
        int mapped = mapear(k);
        if (mapped == 0) return false;
        retro.sendKeyEvent(e.getAction(), mapped, porta(e.getDevice()));
        return true;
    }

    private boolean tratarMotion(MotionEvent e) {
        if (retro == null || menuAberto || e == null) return false;
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) == 0) return false;
        int port = porta(e.getDevice());
        // SNES, fliperama e o controle digital do PS1 não têm analógico: o stick esquerdo também vira direcional.
        float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        float sx = e.getAxisValue(MotionEvent.AXIS_X), sy = e.getAxisValue(MotionEvent.AXIS_Y);
        if (hx == 0 && hy == 0) {
            hx = Math.abs(sx) > 0.5f ? Math.signum(sx) : 0;
            hy = Math.abs(sy) > 0.5f ? Math.signum(sy) : 0;
        }
        retro.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, hx, hy, port);
        retro.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y), port);
        retro.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                e.getAxisValue(MotionEvent.AXIS_Z), e.getAxisValue(MotionEvent.AXIS_RZ), port);
        return true;
    }

    @Override
    protected void onPause() {
        pausadoEm = isFinishing() ? 0 : android.os.SystemClock.uptimeMillis();
        gravarSram();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (!menuAberto) abrirMenu();
    }

    private void abrirMenu() {
        if (menuAberto || retro == null) return;
        menuAberto = true;
        retro.setAudioEnabled(false);
        retro.setFrameSpeed(0);   // 0 quadros por ciclo: o jogo congela enquanto o menu está aberto
        String[] itens = {
                getString(R.string.emu_continue),
                getString(R.string.emu_save_state),
                getString(R.string.emu_load_state),
                getString(R.string.emu_exit)
        };
        new AlertDialog.Builder(this)
                .setItems(itens, (d, which) -> {
                    if (which == 1) salvarEstado();
                    else if (which == 2) carregarEstado();
                    else if (which == 3) {
                        gravarSram();
                        finish();
                        return;
                    }
                    fecharMenu();
                })
                .setOnCancelListener(d -> fecharMenu())
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pausadoEm = 0;
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_SAIR, false)) getWindow().getDecorView().post(this::perguntarSair);
    }

    @Override
    protected void onDestroy() {
        pausadoEm = 0;
        super.onDestroy();
    }

    private void perguntarSair() {
        if (menuAberto || retro == null) return;
        menuAberto = true;
        retro.setAudioEnabled(false);
        retro.setFrameSpeed(0);   // 0 quadros por ciclo: o jogo congela enquanto o menu está aberto
        String[] itens = {getString(R.string.emu_save_exit), getString(R.string.emu_exit_no_save),
                getString(R.string.emu_crt, getResources().getStringArray(R.array.emu_crt_niveis)[crtNivel()]),
                getString(R.string.emu_continue)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.emu_exit_title)
                .setItems(itens, (d, which) -> {
                    if (which == 2) {
                        // Depois que este menu termina de fechar: dois diálogos trocando no mesmo instante derrubam o app no Android 14 com acessibilidade ligada.
                        getWindow().getDecorView().postDelayed(this::escolherCrt, 200);
                        return;
                    }
                    if (which == 3) {
                        fecharMenu();
                        return;
                    }
                    if (which == 0) salvarEstado();
                    gravarSram();
                    finish();   // volta para a JogosActivity, que está embaixo na pilha
                })
                .setOnCancelListener(d -> fecharMenu())
                .show();
    }

    private android.view.View tubo;

    /**
     * Reforço do shader CRT da LibretroDroid, que é fraco: scanlines marcadas (1 linha escura a cada 3 px)
     * e vinheta nas bordas, desenhadas por cima do jogo. Estático: só redesenha quando muda de tamanho.
     */
    private static final class TuboView extends android.view.View {
        private final android.graphics.Paint linhas = new android.graphics.Paint();
        private final android.graphics.Paint vinheta = new android.graphics.Paint();

        TuboView(Context c) {
            super(c);
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(1, 3, android.graphics.Bitmap.Config.ARGB_8888);
            b.setPixel(0, 2, 0x8C000000);
            linhas.setShader(new android.graphics.BitmapShader(b,
                    android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT));
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            vinheta.setShader(new android.graphics.RadialGradient(w / 2f, h / 2f, (float) Math.hypot(w, h) / 2f,
                    new int[]{0x00000000, 0x00000000, 0xB3000000}, new float[]{0f, 0.6f, 1f},
                    android.graphics.Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            c.drawPaint(linhas);
            c.drawPaint(vinheta);
        }
    }

    /** 0 desligado, 1 fraco, 2 médio, 3 forte. Vale para todos os jogos e fica lembrado. */
    private int crtNivel() {
        return getSharedPreferences("emulador", MODE_PRIVATE).getInt("crt_nivel", 0);
    }

    /** Fraco: só o shader da LibretroDroid. Médio e forte: shader + scanlines e vinheta por cima, mais escuras no forte. */
    private void aplicarCrt(int nivel) {
        retro.setShader(nivel > 0 ? ShaderConfig.CRT.INSTANCE : ShaderConfig.Default.INSTANCE);
        tubo.setVisibility(nivel >= 2 ? android.view.View.VISIBLE : android.view.View.GONE);
        tubo.setAlpha(nivel == 2 ? 0.55f : 1f);
    }

    private void escolherCrt() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.emu_crt_title)
                .setSingleChoiceItems(R.array.emu_crt_niveis, crtNivel(), (d, nivel) -> {
                    getSharedPreferences("emulador", MODE_PRIVATE).edit().putInt("crt_nivel", nivel).apply();
                    aplicarCrt(nivel);
                    d.dismiss();
                })
                .setOnDismissListener(d -> fecharMenu())
                .show();
    }

    private void fecharMenu() {
        menuAberto = false;
        if (retro != null) {
            retro.setFrameSpeed(1);
            retro.setAudioEnabled(true);
        }
    }

    private void salvarEstado() {
        try {
            byte[] data = retro.serializeState(false);
            if (escrever(arquivoEstado(), data)) Toast.makeText(this, R.string.emu_saved, Toast.LENGTH_SHORT).show();
            else Toast.makeText(this, R.string.emu_save_failed, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.emu_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void carregarEstado() {
        byte[] data = ler(arquivoEstado());
        if (data == null) {
            Toast.makeText(this, R.string.emu_load_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (retro.unserializeState(data, false)) Toast.makeText(this, R.string.emu_loaded, Toast.LENGTH_SHORT).show();
            else Toast.makeText(this, R.string.emu_load_failed, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.emu_load_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void gravarSram() {
        if (retro == null) return;
        try {
            escrever(arquivoSram(), retro.serializeSRAM(false));
        } catch (Exception ignored) {
        }
    }

    private File arquivoSram() {
        File dir = getExternalFilesDir("saves");
        return new File(dir != null ? dir : getFilesDir(), stem + ".srm");
    }

    private File arquivoEstado() {
        File dir = getExternalFilesDir("states");
        return new File(dir != null ? dir : getFilesDir(), stem + ".state");
    }

    /** Jogador pela ordem em que cada controle aparece. O getControllerNumber da TV conta entradas virtuais e põe o 1º gamepad como jogador 2. */
    private final java.util.List<Integer> controles = new java.util.ArrayList<>();

    private int porta(InputDevice d) {
        if (d == null || !d.isExternal()) return 0;   // controle remoto e teclado da TV: jogador 1
        int i = controles.indexOf(d.getId());
        if (i < 0) {
            controles.add(d.getId());
            i = controles.size() - 1;
        }
        return Math.min(i, 3);
    }

    /** Controle remoto: OK vira A. Gamepad Android troca A/B e X/Y como a LibretroDroid. */
    private static int mapear(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return KeyEvent.KEYCODE_BUTTON_A;
            case KeyEvent.KEYCODE_BUTTON_A:
                return KeyEvent.KEYCODE_BUTTON_B;
            case KeyEvent.KEYCODE_BUTTON_B:
                return KeyEvent.KEYCODE_BUTTON_A;
            case KeyEvent.KEYCODE_BUTTON_X:
                return KeyEvent.KEYCODE_BUTTON_Y;
            case KeyEvent.KEYCODE_BUTTON_Y:
                return KeyEvent.KEYCODE_BUTTON_X;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                return keyCode;
            default:
                return 0;
        }
    }

    private static byte[] ler(File f) {
        if (f == null || !f.isFile() || f.length() == 0 || f.length() > 16 * 1024 * 1024) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return n == buf.length ? buf : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean escrever(File f, byte[] data) {
        if (f == null || data == null) return false;
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        } catch (Exception e) {
            tmp.delete();
            return false;
        }
        if (f.exists() && !f.delete()) {
            tmp.delete();
            return false;
        }
        return tmp.renameTo(f);
    }
}
