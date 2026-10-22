package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Biblioteca de jogos: toda ROM em Android/media/com.gathin.tvhome/Jogos/{SNES,Fliperama,PS1} vira um jogo,
 * e também as de uma pasta Jogos/ na raiz de qualquer pendrive (mesmas subpastas, jogos.json e capas próprios).
 * Jogos/jogos.json (opcional) dá nome, ano, jogadores e capa por arquivo; sem ele, o nome vem do arquivo.
 * No fliperama o nome do .zip tem que ser o nome oficial do jogo (kof97.zip, mslugx.zip...).
 */
final class Jogos {
    static final String PASTA = "/storage/emulated/0/Android/media/com.gathin.tvhome/Jogos";
    static final String JSON = "jogos.json";

    enum Sistema {
        // A cor vem do próprio console: o roxo dos botões do Super Nintendo, o dourado do Neo Geo, o azul do PlayStation.
        SNES("Super Nintendo", "SNES", "libsnes9x.so", 0xFF8E7CF0, new String[]{".sfc", ".smc", ".zip"}),
        FLIPERAMA("Fliperama", "Fliperama", "libfbneo.so", 0xFFF2B531, new String[]{".zip"}),
        PS1("PlayStation", "PS1", "libpcsx_rearmed.so", 0xFF4F86F7, new String[]{".chd", ".pbp", ".cue", ".ccd", ".m3u", ".iso", ".bin", ".img"});

        final String nome, pasta, nucleo;
        final int cor;
        final String[] extensoes;

        Sistema(String nome, String pasta, String nucleo, int cor, String[] extensoes) {
            this.nome = nome;
            this.cor = cor;
            this.pasta = pasta;
            this.nucleo = nucleo;
            this.extensoes = extensoes;
        }
    }

    static final class Jogo {
        final Sistema sistema;
        final File base, rom;
        final String nome, jogadores;
        final int ano;
        final File capa;

        Jogo(Sistema sistema, File base, File rom, String nome, int ano, String jogadores, File capa) {
            this.sistema = sistema;
            this.base = base;
            this.rom = rom;
            this.nome = nome;
            this.ano = ano;
            this.jogadores = jogadores;
            this.capa = capa;
        }

        /** "SNES/smw.smc": a mesma chave do jogos.json e da lista de recentes. */
        String chave() {
            return sistema.pasta + "/" + rom.getName();
        }
    }

    static File pastaJogos() {
        return new File(PASTA);
    }

    /** Cria SNES, Fliperama e PS1 na pasta de mídia do app. */
    static File criarPastas(Context c) {
        File base = pastaJogos();
        File[] media = c.getExternalMediaDirs();
        if (media != null && media.length > 0 && media[0] != null) {
            base = new File(media[0], "Jogos");
        }
        for (Sistema s : Sistema.values()) new File(base, s.pasta).mkdirs();
        return base;
    }

    /**
     * Raiz de cada pendrive montado. Pelo StorageManager: o app não consegue listar /storage,
     * mas abre /storage/XXXX-XXXX direto com o acesso a todos os arquivos.
     */
    static List<File> pendrives(Context c) {
        List<File> out = new ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return out;
        android.os.storage.StorageManager sm = c.getSystemService(android.os.storage.StorageManager.class);
        for (android.os.storage.StorageVolume v : sm.getStorageVolumes()) {
            if (v.isRemovable() && android.os.Environment.MEDIA_MOUNTED.equals(v.getState()) && v.getDirectory() != null) {
                out.add(v.getDirectory());
            }
        }
        return out;
    }

    /** Jogos/ do primeiro pendrive, exista a pasta ou não; null sem pendrive. */
    static File pendrive(Context c) {
        List<File> p = pendrives(c);
        return p.isEmpty() ? null : new File(p.get(0), "Jogos");
    }

    static boolean naTv(Jogo j) {
        return j.base.equals(pastaJogos());
    }

    static final int OK = 0, SEM_ESPACO = 1, ERRO = 2;

    /**
     * Move o jogo para outra pasta Jogos/: a ROM e as faixas que ela abre, a capa e a linha do jogos.json.
     * Copia tudo antes de apagar a origem; se algo falhar no meio, a origem fica intacta e as cópias somem.
     */
    static int mover(Jogo j, File destino) {
        File origemSis = new File(j.base, j.sistema.pasta);
        File destinoSis = new File(destino, j.sistema.pasta);
        List<File> arquivos = arquivosDoJogo(j.rom);
        long total = 0;
        for (File f : arquivos) total += f.length();
        if (j.capa != null && j.capa.isFile()) total += j.capa.length();
        if (!destinoSis.isDirectory() && !destinoSis.mkdirs()) return ERRO;
        if (destino.getUsableSpace() < total + (8L << 20)) return SEM_ESPACO;

        List<File> copiados = new ArrayList<>();
        try {
            for (File f : arquivos) {
                File alvo = new File(destinoSis, origemSis.toPath().relativize(f.toPath()).toString());
                File pai = alvo.getParentFile();
                if (pai != null) pai.mkdirs();
                Files.copy(f.toPath(), alvo.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copiados.add(alvo);
            }
            String capaNova = null;
            if (j.capa != null && j.capa.isFile()) {
                File alvo = new File(new File(destino, "capas"), j.capa.getName());
                alvo.getParentFile().mkdirs();
                Files.copy(j.capa.toPath(), alvo.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copiados.add(alvo);
                capaNova = "capas/" + j.capa.getName();
            }
            // A linha do jogos.json vai junto, com a capa apontando para a pasta capas/ do destino.
            File jsonOrigem = new File(j.base, JSON), jsonDestino = new File(destino, JSON);
            JSONArray origem = lerArray(jsonOrigem);
            JSONObject linha = origem != null ? achar(origem, j.chave()) : null;
            if (linha != null) {
                JSONArray dest = lerArray(jsonDestino);
                if (dest == null) dest = new JSONArray();   // json do destino quebrado: começa um novo em vez de travar
                remover(dest, j.chave());
                JSONObject nova = new JSONObject(linha.toString());
                if (capaNova != null) nova.put("capa", capaNova);
                dest.put(nova);
                gravar(jsonDestino, dest);
            }

            // Tudo copiado: agora sim apaga a origem.
            for (File f : arquivos) f.delete();
            if (origem != null && linha != null) {
                remover(origem, j.chave());
                gravar(jsonOrigem, origem);
            }
            if (j.capa != null && j.capa.isFile() && (origem == null || !usaCapa(origem, j.capa, j.base))) j.capa.delete();
            return OK;
        } catch (Exception e) {
            for (File f : copiados) f.delete();
            return ERRO;
        }
    }

    /** Apaga a ROM (e as faixas que ela abre), a linha do jogos.json e a capa, se nenhum outro jogo a usar. */
    static boolean apagar(Jogo j) {
        boolean ok = true;
        for (File f : arquivosDoJogo(j.rom)) ok &= f.delete();
        File json = new File(j.base, JSON);
        JSONArray a = lerArray(json);
        try {
            if (a != null && achar(a, j.chave()) != null) {
                remover(a, j.chave());
                gravar(json, a);
            }
        } catch (Exception e) {
            ok = false;
        }
        if (j.capa != null && j.capa.isFile() && a != null && !usaCapa(a, j.capa, j.base)) j.capa.delete();
        return ok;
    }

    /** A ROM e o que ela referencia: as faixas de um .cue, os discos de um .m3u, o .img/.sub de um .ccd. */
    static List<File> arquivosDoJogo(File rom) {
        List<File> out = new ArrayList<>();
        out.add(rom);
        String n = rom.getName().toLowerCase(Locale.ROOT);
        File dir = rom.getParentFile();
        try {
            if (n.endsWith(".cue") || n.endsWith(".m3u")) {
                for (String l : Files.readAllLines(rom.toPath(), StandardCharsets.ISO_8859_1)) {
                    String t = l.trim(), nome = null;
                    if (n.endsWith(".cue")) {
                        int a = t.indexOf('"'), b = t.lastIndexOf('"');
                        if (t.toUpperCase(Locale.ROOT).startsWith("FILE") && a >= 0 && b > a) nome = t.substring(a + 1, b);
                    } else if (!t.isEmpty() && !t.startsWith("#")) {
                        nome = t;
                    }
                    File f = nome != null ? new File(dir, nome) : null;
                    if (f == null || !f.isFile()) continue;
                    for (File g : f.getName().toLowerCase(Locale.ROOT).endsWith(".cue") ? arquivosDoJogo(f) : java.util.Collections.singletonList(f)) {
                        if (!out.contains(g)) out.add(g);
                    }
                }
            } else if (n.endsWith(".ccd")) {
                for (String ext : new String[]{".img", ".sub"}) {
                    File f = new File(dir, semExtensao(rom.getName()) + ext);
                    if (f.isFile()) out.add(f);
                }
            }
        } catch (Exception ignored) {
            // .cue ilegível: move só o que der para identificar.
        }
        return out;
    }

    /** null quando o arquivo existe mas não é um JSON válido: aí ninguém regrava por cima dele. */
    private static JSONArray lerArray(File f) {
        if (!f.isFile()) return new JSONArray();
        try {
            return new JSONArray(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static void gravar(File f, JSONArray a) throws Exception {
        Files.write(f.toPath(), a.toString(2).replace("\\/", "/").getBytes(StandardCharsets.UTF_8));
    }

    private static JSONObject achar(JSONArray a, String chave) {
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && chave.equals(o.optString("arquivo"))) return o;
        }
        return null;
    }

    private static void remover(JSONArray a, String chave) {
        for (int i = a.length() - 1; i >= 0; i--) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && chave.equals(o.optString("arquivo"))) a.remove(i);
        }
    }

    /** Outro jogo que fica na origem ainda usa esta capa? */
    private static boolean usaCapa(JSONArray a, File capa, File base) {
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !o.optString("capa").isEmpty() && new File(base, o.optString("capa")).equals(capa)) return true;
        }
        return false;
    }

    /** A pasta interna e a Jogos/ de cada pendrive que tiver uma. */
    private static List<File> bases(Context c) {
        List<File> out = new ArrayList<>();
        out.add(pastaJogos());
        for (File v : pendrives(c)) {
            File j = new File(v, "Jogos");
            if (j.isDirectory()) out.add(j);
        }
        return out;
    }

    /** Todas as ROMs, por sistema e em ordem alfabética do nome mostrado. */
    static List<Jogo> listar(Context c) {
        List<Jogo> out = new ArrayList<>();
        for (Sistema s : Sistema.values()) {
            List<Jogo> doSistema = new ArrayList<>();
            for (File base : bases(c)) lerPasta(base, s, lerJson(new File(base, JSON)), doSistema);
            doSistema.sort((a, b) -> a.nome.compareToIgnoreCase(b.nome));
            out.addAll(doSistema);
        }
        return out;
    }

    private static void lerPasta(File base, Sistema s, Map<String, JSONObject> info, List<Jogo> doSistema) {
        File[] files = new File(base, s.pasta).listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isFile() || !aceita(s, f, files)) continue;
            JSONObject j = info.get(s.pasta + "/" + f.getName());
            String nome = j != null ? j.optString("nome", "") : "";
            String capa = j != null ? j.optString("capa", "") : "";
            doSistema.add(new Jogo(s, base, f,
                    nome.isEmpty() ? semExtensao(f.getName()) : nome,
                    j != null ? j.optInt("ano", 0) : 0,
                    j != null ? j.optString("jogadores", "") : "",
                    capa.isEmpty() ? null : new File(base, capa)));
        }
    }

    /** Extensão do sistema; fora o neogeo.zip (BIOS) e as faixas .bin/.img que um .cue/.ccd de mesmo nome já abre. */
    private static boolean aceita(Sistema s, File f, File[] irmaos) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.startsWith(".") || n.equals("neogeo.zip")) return false;
        boolean ext = false;
        for (String e : s.extensoes) ext |= n.endsWith(e);
        if (!ext) return false;
        if (n.endsWith(".bin") || n.endsWith(".img")) {
            String raiz = semExtensao(n);
            for (File o : irmaos) {
                String on = o.getName().toLowerCase(Locale.ROOT);
                if (on.equals(raiz + ".cue") || on.equals(raiz + ".ccd")) return false;
            }
        }
        return true;
    }

    /** jogos.json: [{"arquivo": "SNES/smw.smc", "nome": "...", "ano": 1991, "jogadores": "...", "capa": "capas/smw.jpg"}]. */
    private static Map<String, JSONObject> lerJson(File f) {
        Map<String, JSONObject> m = new HashMap<>();
        if (!f.isFile()) return m;
        try {
            JSONArray a = new JSONArray(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && !o.optString("arquivo", "").isEmpty()) m.put(o.getString("arquivo"), o);
            }
        } catch (Exception e) {
            // JSON quebrado não some com os jogos: eles aparecem com o nome do arquivo.
        }
        return m;
    }

    private static String semExtensao(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    /** Abre o jogo neste APK, pelo núcleo empacotado. */
    static boolean jogar(Context c, Jogo j) {
        if (!j.rom.isFile()) return false;
        Intent i = new Intent(c, EmuladorActivity.class);
        i.putExtra(EmuladorActivity.EXTRA_ROM, j.rom.getAbsolutePath());
        i.putExtra(EmuladorActivity.EXTRA_CORE, j.sistema.nucleo);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Jogos() {}
}
