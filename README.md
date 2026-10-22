# androidtv-game

Launcher para Android TV com aba de **jogos retrô** embutida (Super Nintendo, fliperama e PlayStation), papel de parede da pasta DCIM e clima na tela inicial. Fork do [CleanYourTV](https://github.com/Gathin23/cleanyourtv) de Gathin (MIT); o README original está em [docs/README-cleanyourtv.md](docs/README-cleanyourtv.md).

## O que tem

- **Tela inicial** leve, sem anúncios nem recomendações, com clima de Cuiabá (troque em `Clima.java`).
- **Aba Jogos**: lê qualquer ROM das pastas, com nome, ano e capa vindos de um `jogos.json` opcional. Lê também uma pasta `Jogos/` no pendrive.
- **Emulador dentro do APK** (LibretroDroid + núcleos snes9x, fbneo e pcsx_rearmed): não precisa do RetroArch.
  - Controle USB/Bluetooth, até 4 jogadores; o analógico também serve de direcional.
  - Botão Home do controle: pausa e pergunta se quer salvar e sair. Ao abrir de novo, pergunta se continua de onde parou.
  - Efeito de TV de tubo (CRT) em 4 níveis, no mesmo menu do Home.
  - Pede o modo jogo da TV (menos atraso no controle).
- **Segurar OK num jogo**: mover entre a TV e o pendrive, ou apagar.
- **Papel de parede**: Ajustes → Aparência → Escolher imagem (fotos da pasta DCIM).
- **Cadastro.exe** (Windows): cadastra ROM, capa e nome no pendrive e atualiza o `jogos.json`.

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

## Licenças

- Launcher: MIT, © Gathin (CleanYourTV) e alterações deste fork.
- [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) e os núcleos libretro em `launcher/app/src/main/jniLibs` ([snes9x](https://github.com/libretro/snes9x), [FBNeo](https://github.com/libretro/FBNeo), [PCSX ReARMed](https://github.com/libretro/pcsx_rearmed)) têm licenças próprias (GPL e licenças não comerciais). Uso pessoal; não venda o APK.
- Nenhuma ROM ou BIOS acompanha este repositório.
