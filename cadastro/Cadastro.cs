// Cadastro de jogos no pendrive (o .exe fica na raiz dele): copia a ROM e a capa para <unidade>\Jogos e atualiza o jogos.json que o launcher lê.
// Compilar: C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe /target:winexe /r:System.Web.Extensions.dll Cadastro.cs
// Sem janela (para testar): Cadastro.exe <unidade> <console> <rom> <nome> [capa] [ano] [jogadores]
using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Text;
using System.Web.Script.Serialization;
using System.Windows.Forms;

static class Cadastro
{
    static readonly string[] Consoles = { "NES", "SNES", "Fliperama", "PS1" };

    [STAThread]
    static int Main(string[] args)
    {
        if (args.Length >= 4)
        {
            try
            {
                Console.WriteLine(Cadastrar(args[0], args[1], args[2], args[3],
                    args.Length > 4 ? args[4] : "", args.Length > 5 ? args[5] : "", args.Length > 6 ? args[6] : ""));
                return 0;
            }
            catch (Exception e)
            {
                Console.WriteLine("ERRO: " + e.Message);
                return 1;
            }
        }
        Application.EnableVisualStyles();
        Application.Run(Janela());
        return 0;
    }

    /// Copia ROM (e as faixas de um .cue) e capa, e grava/atualiza a linha do jogo no jogos.json. Devolve a mensagem de sucesso.
    static string Cadastrar(string unidade, string console, string rom, string nome, string capa, string ano, string jogadores)
    {
        if (!Consoles.Contains(console)) throw new Exception("Console inválido: " + console);
        if (!File.Exists(rom)) throw new Exception("ROM não encontrada.");
        if (nome.Trim() == "") throw new Exception("Preencha o nome.");
        if (capa != "" && !File.Exists(capa)) throw new Exception("Capa não encontrada.");
        int anoNum = 0;
        if (ano.Trim() != "" && !int.TryParse(ano.Trim(), out anoNum)) throw new Exception("Ano tem que ser um número.");

        string jogos = Path.Combine(unidade, "Jogos");
        string pastaSis = Path.Combine(jogos, console);
        Directory.CreateDirectory(pastaSis);
        foreach (string f in ArquivosDoJogo(rom))
            File.Copy(f, Path.Combine(pastaSis, Path.GetFileName(f)), true);

        string capaRel = null;
        if (capa != "")
        {
            string pastaCapas = Path.Combine(jogos, "capas");
            Directory.CreateDirectory(pastaCapas);
            // Capa com o nome da ROM: dois jogos com capa "cover.jpg" não se sobrescrevem.
            string nomeCapa = Path.GetFileNameWithoutExtension(rom) + Path.GetExtension(capa).ToLowerInvariant();
            File.Copy(capa, Path.Combine(pastaCapas, nomeCapa), true);
            capaRel = "capas/" + nomeCapa;
        }

        string jsonPath = Path.Combine(jogos, "jogos.json");
        var lista = new List<Dictionary<string, object>>();
        if (File.Exists(jsonPath))
        {
            string txt = File.ReadAllText(jsonPath, Encoding.UTF8).Trim();
            // JSON quebrado: para aqui em vez de regravar por cima e perder o que já estava cadastrado.
            if (txt != "") lista = new JavaScriptSerializer().Deserialize<List<Dictionary<string, object>>>(txt);
        }
        string arquivo = console + "/" + Path.GetFileName(rom);
        var antigo = lista.FirstOrDefault(o => o.ContainsKey("arquivo") && (o["arquivo"] as string) == arquivo);
        // Recadastrar sem escolher capa mantém a que já estava.
        if (capaRel == null && antigo != null && antigo.ContainsKey("capa")) capaRel = antigo["capa"] as string;
        lista.RemoveAll(o => o.ContainsKey("arquivo") && (o["arquivo"] as string) == arquivo);
        var jogo = new Dictionary<string, object> { { "arquivo", arquivo }, { "nome", nome.Trim() } };
        if (anoNum > 0) jogo["ano"] = anoNum;
        if (jogadores.Trim() != "") jogo["jogadores"] = jogadores.Trim();
        if (capaRel != null) jogo["capa"] = capaRel;
        lista.Add(jogo);
        File.WriteAllText(jsonPath, Json(lista), new UTF8Encoding(false));
        return "Cadastrado: " + nome.Trim() + " (" + arquivo + ")";
    }

    /// A ROM e o que um .cue referencia (FILE "faixa.bin"); senão só a ROM.
    static List<string> ArquivosDoJogo(string rom)
    {
        var out_ = new List<string> { rom };
        if (!rom.EndsWith(".cue", StringComparison.OrdinalIgnoreCase)) return out_;
        foreach (string l in File.ReadAllLines(rom))
        {
            string t = l.Trim();
            int a = t.IndexOf('"'), b = t.LastIndexOf('"');
            if (!t.StartsWith("FILE", StringComparison.OrdinalIgnoreCase) || a < 0 || b <= a) continue;
            string f = Path.Combine(Path.GetDirectoryName(rom), t.Substring(a + 1, b - a - 1));
            if (File.Exists(f) && !out_.Contains(f)) out_.Add(f);
        }
        return out_;
    }

    /// Uma linha por campo, para continuar fácil de editar à mão.
    static string Json(List<Dictionary<string, object>> lista)
    {
        var js = new JavaScriptSerializer();
        var sb = new StringBuilder("[\n");
        for (int i = 0; i < lista.Count; i++)
        {
            sb.Append("  {\n");
            var campos = lista[i].ToList();
            for (int k = 0; k < campos.Count; k++)
                sb.Append("    ").Append(js.Serialize(campos[k].Key)).Append(": ").Append(js.Serialize(campos[k].Value))
                  .Append(k < campos.Count - 1 ? ",\n" : "\n");
            sb.Append(i < lista.Count - 1 ? "  },\n" : "  }\n");
        }
        return sb.Append("]\n").ToString().Replace("\\u0027", "'");
    }

    static Form Janela()
    {
        var f = new Form { Text = "Cadastrar jogo no pendrive", Width = 560, Height = 380, FormBorderStyle = FormBorderStyle.FixedDialog, MaximizeBox = false, StartPosition = FormStartPosition.CenterScreen, Font = new Font("Segoe UI", 10) };
        int y = 16;
        Func<string, Control, Control> linha = (rotulo, campo) =>
        {
            f.Controls.Add(new Label { Text = rotulo, Left = 16, Top = y + 4, Width = 100 });
            campo.Left = 120; campo.Top = y; campo.Width = campo.Width == 0 ? 400 : campo.Width;
            f.Controls.Add(campo);
            y += 38;
            return campo;
        };
        var unidade = (TextBox)linha("Pendrive", new TextBox { Text = Path.GetPathRoot(Application.ExecutablePath), Width = 80 });
        var console = (ComboBox)linha("Console", new ComboBox { DropDownStyle = ComboBoxStyle.DropDownList, Width = 160 });
        console.Items.AddRange(Consoles);
        console.SelectedIndex = 0;
        var rom = (TextBox)linha("ROM", new TextBox { Width = 300 });
        var capa = (TextBox)linha("Capa", new TextBox { Width = 300 });
        var nome = (TextBox)linha("Nome", new TextBox());
        var ano = (TextBox)linha("Ano", new TextBox { Width = 80 });
        var jogadores = (TextBox)linha("Jogadores", new TextBox { Width = 200, Text = "1 jogador" });

        Action<TextBox, string, int> procurar = (alvo, filtro, top) =>
        {
            var b = new Button { Text = "Escolher…", Left = 428, Top = top - 1, Width = 92, Height = 28 };
            b.Click += (s, e) =>
            {
                using (var d = new OpenFileDialog { Filter = filtro })
                    if (d.ShowDialog() == DialogResult.OK)
                    {
                        alvo.Text = d.FileName;
                        // Nome sugerido a partir do arquivo da ROM, só se ainda estiver vazio.
                        if (alvo == rom && nome.Text == "") nome.Text = Path.GetFileNameWithoutExtension(d.FileName);
                    }
            };
            f.Controls.Add(b);
        };
        procurar(rom, "ROMs|*.nes;*.sfc;*.smc;*.zip;*.chd;*.pbp;*.cue;*.ccd;*.m3u;*.iso;*.bin;*.img|Todos|*.*", rom.Top);
        procurar(capa, "Imagens|*.png;*.jpg;*.jpeg;*.webp", capa.Top);

        var ok = new Button { Text = "Cadastrar", Left = 120, Top = y + 4, Width = 140, Height = 34 };
        ok.Click += (s, e) =>
        {
            try
            {
                f.Cursor = Cursors.WaitCursor;
                string msg = Cadastrar(unidade.Text, (string)console.SelectedItem, rom.Text, nome.Text, capa.Text.Trim(), ano.Text, jogadores.Text);
                MessageBox.Show(msg, "Pronto");
                rom.Text = capa.Text = nome.Text = ano.Text = "";
            }
            catch (Exception ex)
            {
                MessageBox.Show(ex.Message, "Não deu para cadastrar", MessageBoxButtons.OK, MessageBoxIcon.Warning);
            }
            finally
            {
                f.Cursor = Cursors.Default;
            }
        };
        f.Controls.Add(ok);
        f.AcceptButton = ok;
        return f;
    }
}
