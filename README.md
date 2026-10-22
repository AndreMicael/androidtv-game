# androidtv-game

Launcher para Android TV com aba de **jogos retrô** embutida (Super Nintendo, fliperama e PlayStation), papel de parede da pasta DCIM e clima na tela inicial. Fork do [CleanYourTV](https://github.com/Gathin23/cleanyourtv) de Gathin (MIT); o README original está em [docs/README-cleanyourtv.md](docs/README-cleanyourtv.md).

## Funcionalidades

### Tela inicial
- Relógio, data e saudação com a temperatura de Cuiabá (Open-Meteo, sem chave; troque a cidade em `Clima.java`).
- Fileira fixa de apps em cima e grade com o resto, com um destaque de vidro que acompanha o foco. OK abre o app; segurar OK abre os detalhes dele no sistema.
- Barra de cima, em pílulas:
  - **Jogos**: abre a aba de jogos.
  - **Entradas**: HDMI, AV e sintonizador da TV (some se a TV não tiver entradas).
  - **Wi-Fi**: mostra o nome da rede e abre os ajustes de rede.
  - **Ajustes**: TV ao vivo, Liberar memória, Aparência e Ajustes do sistema.
- **Aparência**: fundo Preto, Carvão, Marinho, Verde ou Vinho, ou uma foto da pasta DCIM como papel de parede.
- **Liberar memória**: fecha os apps parados em segundo plano.

### TV ao vivo
- **Ajustes → TV ao vivo**: 263 canais abertos brasileiros (`assets/canais.json`), separados por categoria.
- No player: cima/baixo ou Canal+/Canal− trocam de canal, OK mostra o nome, Voltar sai.

### Jogos
- Uma prateleira por console (Super Nintendo, Fliperama, PlayStation, PlayStation Portable), cada jogo com a cor do console e a capa ao fundo, mais a fileira **Continuar jogando**.
- Lê qualquer ROM das pastas da TV e de uma pasta `Jogos/` no pendrive; nome, ano, jogadores e capa vêm do `jogos.json` (opcional).
- Mostra o espaço livre na TV e no pendrive, e o botão **Atualizar** relê as pastas.
- **Segurar OK num jogo**: mover entre a TV e o pendrive (ROM, faixas do `.cue`, capa e linha do JSON vão juntos; um carregando discreto aparece no canto) ou apagar, com confirmação.

- **PSP**: os jogos ficam em `Jogos/PSP` (`.iso`, `.cso`, `.chd`, `.pbp`) e abrem no app [PPSSPP](https://www.ppsspp.org), que precisa estar instalado. Na primeira vez, escolha no PPSSPP "Usar os Dados Privados do App" para os saves funcionarem.

### Emulador (dentro do APK)
- LibretroDroid com os núcleos snes9x, FBNeo e PCSX ReARMed: não precisa do RetroArch.
- Controle USB ou Bluetooth, até 4 jogadores (o primeiro controle a apertar um botão é o jogador 1); o analógico esquerdo também serve de direcional. O controle remoto também joga.
- **Home do controle** (ou do remoto) no meio do jogo: pausa e pergunta Salvar e sair / Sair sem salvar / Efeito TV de tubo / Continuar.
- **Voltar ou SELECT+START**: menu Continuar / Salvar estado / Carregar estado / Sair.
- Ao abrir um jogo com estado salvo, pergunta se continua de onde parou. O save da bateria do cartucho é gravado sozinho.
- **Efeito TV de tubo (CRT)** em 4 níveis (desligado, fraco, médio, forte), lembrado para todos os jogos.
- Pede o modo jogo da TV (ALLM): menos atraso entre o botão e a tela.

### Protetor de tela
- Relógio fino sobre um brilho suave (`ClockDream`). Ative em Ajustes do sistema → Protetor de tela, ou:
  `adb shell settings put secure screensaver_components com.gathin.tvhome/.ClockDream`

### Serviços opcionais (acessibilidade, ativar pelo adb)
- **InputKeyService**: faz o botão Input do controle remoto abrir o seletor de entradas, que some quando o launcher do Google é desativado.
- **HomeKeyService**: para controles que mandam `ALL_APPS` no botão Home (sticks Xiaomi).
- Ativar (exemplo):
  `adb shell settings put secure enabled_accessibility_services com.gathin.tvhome/.InputKeyService`
  `adb shell settings put secure accessibility_enabled 1`

### Cadastro.exe (Windows)
- Programa de 12 KB que fica na raiz do pendrive: escolha console, ROM, capa e nome, e ele copia tudo para `Jogos/` e atualiza o `jogos.json`. Detalhes em [Cadastro.exe](#cadastroexe-windows).

### Também configurado na TV original (fora deste código)
- **AirPlay** para espelhar iPhone/Mac: [airplay-android-tv](https://github.com/matinzd/airplay-android-tv). Use um nome diferente do nome da TV (ex.: "TV do quarto AirPlay"), senão o Android recusa o anúncio, e deixe o "4K mirroring" desligado se o decodificador H.265 da TV falhar (fica preso em "Conectando").
- **Enviar arquivos pelo PC**: servidor FTP do app Amaze File Manager (FileZilla com `ftps://<ip-da-tv>`, porta 2211, usuário `anonymous`).
- Launcher do Google, Play Filmes, recomendações, feedback e agenda desativados (`pm disable-user`, reversível com `pm enable`).

## Instalar na TV de alguém

Precisa de uma Android TV / Google TV com ARM (o APK é só `armeabi-v7a`, que também roda em TVs de 64 bits) e de um PC na mesma rede.

1. Na TV: Configurações → Sobre → clique 7 vezes em "Build" para liberar as Opções do desenvolvedor; lá, ligue **Depuração USB** / **Depuração de rede**.
2. Baixe o [platform-tools](https://developer.android.com/tools/releases/platform-tools) do Google e, no PC:
   ```
   adb connect <ip-da-tv>:5555
   ```
   Aceite o aviso na tela da TV ("Sempre permitir").
3. Compile e instale (veja [Compilar](#compilar)):
   ```
   adb install -r launcher/app/build/outputs/apk/debug/app-debug.apk
   adb shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
   ```
   Se der `INSTALL_FAILED_VERIFICATION_FAILURE`, desligue o Play Protect na Play Store da TV durante a instalação.
4. Acesso aos arquivos (papel de parede da DCIM e jogos no pendrive):
   ```
   adb shell appops set com.gathin.tvhome MANAGE_EXTERNAL_STORAGE allow
   ```
5. Opcional: se o launcher do Google continuar abrindo no botão Home, desative-o (reversível com `pm enable`):
   ```
   adb shell pm disable-user --user 0 com.google.android.tvlauncher
   ```

## Jogos

Pastas na TV (pelo FTP do Amaze/FileZilla ou `adb push`):

```
Android/media/com.gathin.tvhome/Jogos/
  SNES/        .sfc .smc .zip
  Fliperama/   .zip com o nome oficial do MAME/FBNeo (kof97.zip...), mais o neogeo.zip
  PS1/         .chd .pbp .cue+.bin .iso
  PSP/         .iso .cso .chd (abre no app PPSSPP)
  capas/       imagens das capas
  jogos.json   opcional
```

No pendrive, a mesma estrutura numa pasta `Jogos/` na raiz (FAT32 ou exFAT).

`jogos.json`:

```json
[
  {
    "arquivo": "SNES/SuperMarioWorld.smc",
    "nome": "Super Mario World",
    "ano": 1991,
    "jogadores": "1–2 jogadores",
    "capa": "capas/smw.png"
  }
]
```

Capas prontas: [libretro-thumbnails](https://github.com/libretro-thumbnails) (pasta `Named_Boxarts` de cada console).

Alguns jogos de PS1 precisam da BIOS (`scph5501.bin`), extraída do seu próprio console, em `Android/data/com.gathin.tvhome/files/system/`.

## Cadastro.exe (Windows)

Copie `cadastro/Cadastro.exe` para a raiz do pendrive e abra: escolha console, ROM, capa e nome; ele copia tudo para `<pendrive>\Jogos` e atualiza o `jogos.json`. Para recompilar:

```
C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe /target:winexe /r:System.Web.Extensions.dll /out:Cadastro.exe Cadastro.cs
```

## Compilar

JDK 17 e Android SDK (platform 34, build-tools 34). Em `launcher/`:

```
echo sdk.dir=C:\\Users\\<voce>\\Android\\Sdk > local.properties
gradlew.bat assembleDebug
```

O APK sai em `launcher/app/build/outputs/apk/debug/app-debug.apk`. Para atualizar por cima de uma instalação, use a mesma chave (`~/.android/debug.keystore`) de quem instalou.

## Créditos

Este projeto foi feito em cima do trabalho de outras pessoas:

- **[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv)** (Gathin): o launcher original (antes chamado tv-vibehack), base de todo o código deste repositório. Licença MIT.
- **[Fardeen26/tv-vibe-kit](https://github.com/Fardeen26/tv-vibe-kit)**: o guia que levou ao launcher acima.
- **[Swordfish90/LibretroDroid](https://github.com/Swordfish90/LibretroDroid)**: a biblioteca que roda os emuladores dentro do app.
- **[libretro](https://github.com/libretro)**: os núcleos [snes9x](https://github.com/libretro/snes9x), [FBNeo](https://github.com/libretro/FBNeo) e [PCSX ReARMed](https://github.com/libretro/pcsx_rearmed), e as capas do [libretro-thumbnails](https://github.com/libretro-thumbnails).
- **[matinzd/airplay-android-tv](https://github.com/matinzd/airplay-android-tv)**: o receptor AirPlay usado na TV (não incluso aqui).
- Clima: [Open-Meteo](https://open-meteo.com).

## Licenças

- Launcher: MIT, © Gathin (CleanYourTV) e alterações deste fork.
- [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) e os núcleos libretro em `launcher/app/src/main/jniLibs` ([snes9x](https://github.com/libretro/snes9x), [FBNeo](https://github.com/libretro/FBNeo), [PCSX ReARMed](https://github.com/libretro/pcsx_rearmed)) têm licenças próprias (GPL e licenças não comerciais). Uso pessoal; não venda o APK.
- Nenhuma ROM ou BIOS acompanha este repositório.
