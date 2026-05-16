# Manjiro Companion

> Game booster app — companion oficial do módulo Magisk/KernelSU/APatch [Manjiro Dinamic](../manjiro_dinamic).
> Tema **Tokyo Revengers**. Stack **Kotlin · Jetpack Compose · Material 3**.

| | |
|---|---|
| Min SDK | 29 (Android 10) |
| Target SDK | 34 (Android 14) |
| Compile SDK | 34 |
| Linguagem | Kotlin 2.0 |
| UI | Jetpack Compose · Material 3 |
| Root lib | [libsu](https://github.com/topjohnwu/libsu) (topjohnwu) |
| Idioma | Português brasileiro (default) |
| Licença | Apache 2.0 |

## O que ele faz

- Mostra o estado do motor Manjiro Dinamic em tempo real (Toman em casa · Patrulha · **Modo Mikey** · Recuo · Refúgio).
- Lança jogos com pré-boost e notificação persistente com:
  - 🐉 temperatura do SoC ("Toman")
  - 🔋 temperatura da bateria ("Hinata")
  - ⚡ % de bateria
  - nome do jogo ativo
  - tempo de sessão ao vivo
  - barra de Disciplina (estabilidade Lyapunov, sem jargão)
  - técnicas ativas (atuadores)
- Ficha do membro (long-press num jogo): forma de combate, linha vermelha (temp target), HUD, modo silencioso.
- Crônica: histórico de batalhas + sessão atual + resumo do dia.
- Ajustes: HUD global, forma padrão, painel WebUI avançado, sair do refúgio.

Zero jargão técnico na UI. Termos como Lyapunov / MPC / CBF / throttle / actuator ficam no backend (`manjirod`).

## Como rodar

Pré-requisitos:
- JDK 17
- Android SDK (cmdline-tools 11.0+, platform-tools, platforms;android-34, build-tools;34.0.0)
- Defina `ANDROID_HOME` (ou `ANDROID_SDK_ROOT`) apontando pra sua instalação do SDK.

```bash
./gradlew assembleDebug         # gera APK debug em app/build/outputs/apk/debug/
./gradlew lint                  # roda o lint
```

Pra instalar no celular:
1. Instalar o módulo `manjiro_dinamic` (Magisk / KernelSU / APatch).
2. Conceder permissão root pro app `dev.jtapzg.manjiro.debug` (ou `dev.jtapzg.manjiro` em release).
3. Abrir o app, aceitar root.
4. Tocar a roncar.

## Integração com o daemon

| Caminho | Uso |
|---|---|
| `/data/adb/manjiro_dinamic/state/status.json` | Lido a cada 1–6 s (poll adaptativo) |
| `/data/adb/manjiro_dinamic/state/control.cmd` | Append-only; comandos `game_on`, `game_off`, `safe_clear`, `profile_set`, `reload` |
| `/data/adb/manjiro_dinamic/config/games.json` | Reescrito ao salvar uma ficha |

Schema do `status.json` em <ref_snippet file="app/src/main/java/dev/jtapzg/manjiro/data/ManjiroState.kt" lines="14-44" />.

## Estrutura

```
app/src/main/
├── AndroidManifest.xml
├── java/dev/jtapzg/manjiro/
│   ├── MainActivity.kt
│   ├── ManjiroApp.kt
│   ├── data/             # state, games, repository, root shell
│   ├── service/          # foreground service + notifications
│   ├── ui/theme/         # Color, Type (Rajdhani / JetBrains Mono), Theme
│   ├── ui/toman/         # Hero, GameGrid, FichaMembroSheet, AjustesSheet, ...
│   └── util/             # Translations (Tokyo Revengers vocab), TimeFormat
└── res/
    ├── drawable-nodpi/   # mikey_hero, mikey_ficha, mikey_empty (webp)
    ├── font/             # Rajdhani · JetBrains Mono
    ├── mipmap-*/         # ic_launcher (adaptive + legacy)
    └── values/           # colors, strings (pt-BR), themes
```

## Status

MVP Fase 1 — em desenvolvimento.

## Aviso

Tokyo Revengers / Toman / Mikey / Bonten são marcas de Ken Wakui / Kodansha. Este projeto é fan-art não-comercial.
