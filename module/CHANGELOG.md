# Manjiro Dinamic v1.7.4 — changelog tecnico

**Lancamento:** simbiose APK <-> modulo.
**Foco:** o Manjiro Gaming agora pilota o motor diretamente via novos
comandos no `control.cmd` (mode_set, profile_set, quality, safe_clear,
shutdown). Tudo que o usuario muda no APK (modo, qualidade por jogo,
limpeza de memoria, encerrar sessao) chega no daemon e e refletido de
volta na notificacao + na UI.

```
Sequencia: 1.6.0 -> ... -> 1.7.2 -> 1.7.3 -> 1.7.4
```

---

## Resumo do release

| Arquivo                        | Mudanca   | Detalhe                                                                                                                                                                                                                       |
|--------------------------------|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ManjiroGaming.apk`            | substitui | **Novo build (Manjiro Companion -> Manjiro Gaming):** package `com.jtapzg.manjirogaming`, tema RedMagic, 5 abas, **launcher de jogos integrado** (botao Jogar abre o jogo + aplica modo + libera memoria), **feedback (snackbar) em toda alteracao**, modo Auto que reavalia em tempo real, Estatisticas com sessao registrada pelo proprio servico. |
| `system/bin/manjirod`          | atualiza  | `process_control_cmd` drena toda a fila (antes parava no `head -1`) e reconhece **novos comandos**: `mode_set`, `profile_set`, `quality`, `safe_clear`, `clean_memory`, `shutdown`.                                            |
| `scripts/apk_apply.sh`         | NOVO      | Helper que traduz comandos do APK para parametros do motor. Modo do APK vira `user.conf` (sobrescreve `runtime.conf` via `. source`). Quality por jogo aplica `wm size` quando o jogo esta em foreground. Profile por jogo persiste em `state/apk_profiles.json`. |
| `module.prop`                  | atualiza  | `version=v1.7.4`, `versionCode=27`. Descricao reflete a simbiose.                                                                                                                                                             |
| `BROKEN.md`                    | NOVO      | Registro do que estava quebrado em v1.7.3 e como foi corrigido.                                                                                                                                                               |

---

## O que estava quebrado em v1.7.3 (e foi corrigido)

| Problema                                                                 | Causa-raiz                                                                                                                                                | Correcao em v1.7.4                                                                                                                                                          |
|--------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Notificacao do app nao aparecia quando o jogo abria.                     | O servico do APK era best-effort; nao era foreground service e a notificacao do canal Sessao de Jogo era criada mas nao "promovida".                       | `ManjiroService` agora e foreground service desde `onCreate`, com canal de baixa prioridade persistente (`CHANNEL_GAME`) e canal transient (`CHANNEL_EVENT`) para feedback. Tambem pede `POST_NOTIFICATIONS` em A13+.                  |
| Trocar de modo no APK nao mexia no motor.                                | APK escrevia em `control.cmd` mas o daemon so reconhecia `reload/reset_safe/game_on/game_off/dump` (e ainda truncava apos a primeira linha).               | `process_control_cmd` agora aceita `mode_set <KEY>` e roda `apk_apply.sh mode <KEY>`, que escreve `user.conf` (overlay sobre `runtime.conf`) com `SOC_TARGET_C10/W_THERMAL/W_LATENCY/LOOP_*`. O daemon recarrega o conf na mesma volta. |
| Slider de qualidade por jogo nao tinha efeito.                            | O APK so guardava na database; nada propagava para o motor.                                                                                               | Novo comando `quality <pkg> <pct>` -> `apk_apply.sh quality` aplica `wm size base*pct%` quando o jogo esta em foreground; caso contrario fica salvo em `state/apk_quality.json` para a proxima sessao.                                  |
| "Modo do jogo" nao persistia entre o daemon e o APK.                      | Sem ponte. `profile_set` nao existia.                                                                                                                     | Novo `profile_set <pkg> <profile>` -> `apk_apply.sh profile` persiste em `state/apk_profiles.json`.                                                                       |
| Trocar nome / toggles / modos sem feedback ("clica e nada acontece").    | UI atualizava o state, mas nao havia Toast/Snackbar.                                                                                                       | `MainViewModel` agora emite mensagens num `feedbackFlow`; o `RootScreen` faz `Scaffold` com `SnackbarHost` que mostra a confirmacao em portugues coloquial (ex. "Nome alterado", "Tudo certo! Modo Performance ativado").                |
| Nao dava pra abrir o jogo a partir do APK.                                | O APK so observava o foreground do jogo, nao iniciava.                                                                                                    | `GameLauncher` agora chama `pm.getLaunchIntentForPackage` (com fallback `monkey` via root) **depois** de aplicar modo+memoria. Cada card de jogo no Dashboard/Jogos tem botao "Jogar".                                                |
| Boot-time reapply nao acontecia.                                          | Receiver existia mas nao reaplicava o modo salvo.                                                                                                          | `BootReceiver` -> `repo.reapplyAfterBoot()` -> `ModeApplier.applyGlobal(prefs.globalMode)`.                                                                                |
| Sair do jogo deixava state sujo.                                          | Nenhum comando de "shutdown" era enviado.                                                                                                                  | Novo comando `shutdown` zera `apk_state.json` e leva o motor para `COOLDOWN` se estava `ENGAGED`. O `ShutdownReceiver` e o botao "Recoil" da notificacao usam esse caminho.                                                          |

---

## Novos comandos no `control.cmd`

O APK enfileira no arquivo `/data/adb/manjiro_dinamic/state/control.cmd`
(uma linha por comando). O daemon drena tudo na proxima volta do loop:

| Comando                          | Acao                                                                                          |
|----------------------------------|------------------------------------------------------------------------------------------------|
| `mode_set LITE`                 | Sobrescreve `user.conf` com perfil conservador (target 75.0 C, pesos termicos altos).         |
| `mode_set BALANCED`             | Sobrescreve `user.conf` com perfil padrao.                                                    |
| `mode_set PERFORMANCE`          | Sobrescreve `user.conf` com folga termica (target 83.0 C, pesos de latencia altos).           |
| `mode_set AUTO`                 | Remove `user.conf` (o APK decide dinamicamente baseado em bateria/temp e re-envia `mode_set`).|
| `profile_set <pkg> <profile>`   | Persiste o perfil pretendido pelo APK para o pacote.                                          |
| `quality <pkg> <pct>`           | Aplica `wm size` se `pkg` for o jogo em foreground; senao registra para a proxima sessao.     |
| `safe_clear` / `clean_memory`   | `sync; echo 3 > /proc/sys/vm/drop_caches`. Usado pelo botao "Liberar memoria" do APK.         |
| `game_on [pkg] [profile]`       | Forca transicao para ENGAGED com `pkg` (antes nao aceitava pkg como argumento).               |
| `game_off`                      | Forca transicao para COOLDOWN.                                                                |
| `shutdown`                      | Encerra a sessao de jogo (zera `apk_state.json`, vai para COOLDOWN se estava ENGAGED).        |

---

# Manjiro Dinamic v1.7.3 — changelog técnico

**Lançamento:** rebranding + evolução do app embarcado.
**Foco:** o antigo `Manjiro Companion` foi reescrito visualmente e
expandido em funcionalidades, e agora é entregue como **Manjiro Gaming**
(`com.jtapzg.manjirogaming`, versionCode 26). O motor `manjirod` em si
permanece o do v1.7.2 — esta release é uma atualização *do app embarcado
no módulo*, não do daemon.

```
Sequência: 1.6.0 → 1.6.1 → 1.6.2 → 1.6.3 → 1.6.3.1 → 1.6.4
         → 1.7.0 → 1.7.1 → 1.7.2 → 1.7.3
```

---

## Resumo do release

| Arquivo                 | Mudança    | Detalhe                                                                                                                                                |
|-------------------------|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ManjiroGaming.apk`     | ~17.4 MB   | **Novo.** Substitui `ManjiroCompanion.apk`. Tema RedMagic completo (preto + vermelho), tipografia Rajdhani, 5 abas redesenhadas, slider de qualidade de imagem por jogo, limpeza de memória ao abrir, Modo Automático por bateria/temperatura. |
| `service.sh`            | atualizado | Desinstala o pacote legacy `com.jtapzg.manjiro.companion` se presente, depois instala/atualiza `ManjiroGaming.apk` (`com.jtapzg.manjirogaming`) via `pm install` no primeiro boot completo após o flash. Idempotente. |
| `customize.sh`          | atualizado | Atualiza o set_perm pra `ManjiroGaming.apk` e o banner mostra `v1.7.3 + Manjiro Gaming`.                                                              |
| `module.prop`           | atualizado | `version=v1.7.3`, `versionCode=26`. Descrição menciona slider de qualidade, limpeza de memória e Modo Automático.                                     |
| `manjirod` e scripts    | iguais     | Sem mudança no daemon — comportamento `v1.7.2`.                                                                                                       |

---

## App Manjiro Gaming (v1.0.0)

**Pacote:** `com.jtapzg.manjirogaming`
**Tamanho:** 17.4 MB (debug-signed v2)
**APIs:** minSdk 26, targetSdk 34
**Tema:** dark RedMagic — preto `#0A0A0A`, vermelho `#FF2222`,
cinza-escuro `#1C1C1E`, tipografia Rajdhani.
**Tela inicial / launcher icon:** ícone vermelho de capuz fornecido
pelo `jtapzg`, com adaptativo + monocromático na status bar.

### Telas

1. **Início** — saudação dinâmica `Pronto para jogar, [nome]?`, card do
   modo ativo com glow, mini painel ao vivo (`Desempenho do celular`
   / `Uso de memória` / `Motor gráfico`, refresh 2s), lista de jogos
   adicionados com ícone + última vez jogado + tempo total, FAB
   `+ Adicionar jogo`.
2. **Modos** — 4 cards grandes (`Lite` / `Balanced` / `Performance` /
   `Automático`) com ícones SVG exclusivos e glow vermelho no card
   selecionado.
3. **Jogos** — adicionar via seletor de apps instalados; cada jogo tem:
   modo individual, slider de qualidade `40-100%` (badges
   ⚠ Imagem bem reduzida / Boa fluidez / Alta qualidade / Nativo),
   toggles de Otimização nativa do Android, notificação e limpeza de
   memória ao abrir.
4. **Estatísticas** — tempo jogado hoje/total por jogo, gráfico de
   linha (desempenho + memória), temperatura máxima registrada,
   últimas 5 sessões.
5. **Ajustes** — nome do usuário, iniciar com o sistema, estilo de
   notificação (completa / mínima / desligada), restaurar ao bloquear
   tela, redefinir dados, créditos `jtapzg — github.com/jtapzg`.

### Funcionalidades novas (vs Companion v1.7.2)

- **Slider de qualidade de imagem por jogo (40-100%):**
  aplica `wm size W×H` + `wm density D` proporcional à resolução nativa,
  com guard de pares pra evitar artefatos. Restaura automaticamente ao
  sair do jogo. Persistido em Room.
- **Limpeza de memória ao abrir jogo (root):**
  sequência exata `sync` → `echo 3 > /proc/sys/vm/drop_caches` →
  `am kill-all` → wait 800 ms → `echo 1 > /proc/sys/vm/compact_memory`.
  Pula automaticamente se bateria `< 10%`. Toast discreto
  `✓ Memória liberada · Modo X ativado`.
- **Integração com `cmd game mode`:** Lite=3, Balanced=1, Performance=2
  + `cmd game battery [pkg] enable/disable` conforme o modo.
- **Modo Automático:** bateria `< 20%` → Lite, `20-50%` → Balanced,
  `> 50%` → Performance, temp `> 42°C` força Lite. Reavalia a cada 60s
  enquanto o jogo está ativo. Notificação reflete o modo em tempo real.
- **Restauração robusta:** `wm size reset` + `wm density reset` +
  `animator_duration_scale 1.0` + `cmd game mode set 1` em `onDestroy()`
  do service, no `ACTION_SHUTDOWN` receiver, e quando o jogo sai do
  foreground.
- **Notificação no formato exato:**
  `🎮 Modo Game Ativo — [Jogo]`
  com subtítulo `▸ [Modo] · [X min jogados]`, canal *Sessão de jogo*.
- **Tela de Estatísticas com persistência real:** sessões registradas
  no Room (`manjiro_gaming.db`), gráfico Canvas-based com path para
  desempenho (vermelho) + memória (azul).

### Privacidade (mantida)

- **Zero permissão `INTERNET`** — verificado por
  `aapt2 dump permissions`.
- Banco local Room (`manjiro_gaming.db`) + DataStore preferences.
- `AccessibilityService` lê só `event.packageName` (zero scraping).
- 11 permissões declaradas, todas justificadas:
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
  `POST_NOTIFICATIONS`, `PACKAGE_USAGE_STATS`, `QUERY_ALL_PACKAGES`,
  `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`,
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `BIND_ACCESSIBILITY_SERVICE`,
  `BATTERY_STATS`.

---

## Substituição do pacote legacy

O `service.sh` agora detecta se o pacote antigo
`com.jtapzg.manjiro.companion` ainda está instalado (de uma versão
v1.7.2 do módulo). Se sim, faz `pm uninstall --user 0` antes de
instalar o `ManjiroGaming.apk`. Isso evita ter os dois apps lado a lado
no launcher, já que o `applicationId` mudou.

Fluxo no primeiro boot após flashar v1.7.3:

```
service.sh:
  1. detecta companion.install_pending
  2. pm uninstall com.jtapzg.manjiro.companion      (se existir)
  3. pm install ManjiroGaming.apk                   (com.jtapzg.manjirogaming)
  4. grava companion.installed_vers=v1.7.3
  5. remove pending
```

Log: `/data/adb/manjiro_dinamic/logs/companion_install.log`.

---

## Auditoria

- `sh -n` em `service.sh`, `customize.sh`, `action.sh`, `uninstall.sh`
  e todos os scripts: **PASS**.
- `jq empty` em todos os JSONs de `config/`: **PASS**.
- Audit zero-AI (`chatgpt|claude|GPT|IA|automa[çc][aã]o.inteligente`)
  no APK e em todos os arquivos do módulo: **0 matches**.
- `apksigner verify` no `ManjiroGaming.apk`: **Verified using v2 scheme**.
- `aapt2 dump permissions`: 11 permissões declaradas, **sem
  `INTERNET`**.

---

## Como instalar

1. Flasha `manjiro_dinamic-v1.7.3.zip` pelo Magisk / KSU / APatch.
2. Reinicia. No primeiro boot completo (sys.boot_completed=1 + ~8s),
   o `service.sh` desinstala o Companion legacy (se existir) e instala
   o `ManjiroGaming.apk`.
3. Abre o **Manjiro Gaming** do launcher.
4. Concede as permissões pendentes no banner:
   - `PACKAGE_USAGE_STATS` (Acesso a uso)
   - `POST_NOTIFICATIONS` (Notificações, Android 13+)
   - Acessibilidade (recomendado para detecção sub-100ms)
   - Ignorar otimização de bateria
   - Root no gerenciador (Magisk/KSU/APatch) pro
     `com.jtapzg.manjirogaming`
5. Adiciona seus jogos na aba **Jogos**, configura modo + slider de
   qualidade + toggles. Abre o jogo: a notificação
   `🎮 Modo Game Ativo — [Jogo]` aparece em segundos.

---

— Desenvolvido por **jtapzg** · github.com/jtapzg
