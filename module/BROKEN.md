# BROKEN.md — registro do que estava quebrado em v1.7.3

> Lista honesta do que **nao funcionava** na release anterior e como
> v1.7.4 cobriu cada caso. Tudo aqui ja esta corrigido na release atual,
> mas ficam registrados para rastreabilidade.

---

## 1) APK e modulo nao se enxergavam

**Sintoma:** Trocar de modo / ligar Auto / mexer no slider no APK nao
mudava nada no motor. Voce so via o daemon agir com base no proprio
sensor termico.

**Causa-raiz:** `manjirod` so reconhecia `reload | reset_safe | game_on
| game_off | dump` e ainda truncava `control.cmd` apos a primeira linha
(`head -1`). Comandos novos do APK (`mode_set`, `profile_set`,
`quality`, `safe_clear`, `shutdown`) eram silenciosamente descartados.

**Correcao (v1.7.4):**
- `process_control_cmd` drena **toda** a fila num `while read` antes de
  truncar (move o arquivo para um tmp e processa linha-a-linha).
- Adicionados os novos casos no `case "$op"`.
- Novo helper `scripts/apk_apply.sh` traduz cada comando do APK para
  parametros do motor (`user.conf`, `apk_quality.json`,
  `apk_profiles.json`).

---

## 2) Notificacao nao aparecia

**Sintoma:** "Modo Game Ativo — Free Fire" nunca aparecia, mesmo com o
jogo aberto e o servico do APK rodando.

**Causa-raiz:**
- O servico nao era foreground service de fato (faltava o
  `startForeground` no `onCreate`).
- Sem `POST_NOTIFICATIONS` em Android 13+, todo o canal era barrado em
  silencio.
- Canal estava categorizado como default, entao o sistema rebaixava a
  notificacao quando o usuario abria o jogo.

**Correcao (v1.7.4):**
- `ManjiroService.onCreate` chama `startForeground(NOTIF_ID_SESSION,
  Notif.buildSession(...))` imediatamente.
- `MainActivity` solicita `POST_NOTIFICATIONS` em A13+ via
  `ActivityResultContracts.RequestPermission`.
- Dois canais: `CHANNEL_GAME` (IMPORTANCE_LOW, ongoing) e
  `CHANNEL_EVENT` (IMPORTANCE_DEFAULT, auto-cancel) para feedback.

---

## 3) Cliques no APK sem confirmacao

**Sintoma:** "alterei o nome, mexi no toggle, troquei de modo e o app
nao avisa que mexeu".

**Causa-raiz:** UI atualizava o `StateFlow`, mas nao havia Toast nem
Snackbar. Para o usuario, parecia bug.

**Correcao (v1.7.4):**
- `MainViewModel` ganhou um `Channel<String>` (`feedbackFlow`) que toda
  acao do usuario alimenta com uma mensagem em portugues coloquial:
  - "Nome alterado pra X"
  - "Tudo certo! Modo Performance ativado"
  - "Notificacao ligada"
  - "Memoria liberada · Modo Lite"
  - "Algo deu errado ao ativar. Tenta de novo."
- `RootScreen` faz `Scaffold` com `SnackbarHost` ligado ao
  `feedbackFlow` via `collectLatest`. Tambem dispara `Notif.postEvent`
  para feedback fora do app quando `notifEnabled = true`.

---

## 4) Sem launcher de jogos no APK

**Sintoma:** O APK so monitorava jogos em foreground. Voce precisava
abrir o jogo pelo launcher do sistema, esperar o servico detectar e
*entao* ele agia.

**Causa-raiz:** Faltava a feature.

**Correcao (v1.7.4):**
- Novo `GameLauncher` com:
  - `launch(game, currentDaemonState)`:
    1. Limpa memoria via root (`safe_clear` + `drop_caches`) se
       `mgCleanMemory`.
    2. Aplica modo+profile do jogo via `ModeApplier.applySessionFor`.
    3. Aguarda 250 ms para o motor assentar.
    4. Abre o app via `PackageManager.getLaunchIntentForPackage`
       (fallback `monkey` via root).
- `DashboardScreen` e `GamesScreen` agora tem botao "Jogar" em cada
  card. O feedback ("Abrindo Free Fire... Modo Performance ativado")
  vem via snackbar imediato.

---

## 5) Slider de qualidade nao surtia efeito

**Sintoma:** Mover o slider mudava o numero na UI, mas a tela do jogo
ficava igual.

**Causa-raiz:** O valor so era salvo no Room. Nao havia caminho para o
`wm size`/`wm density`.

**Correcao (v1.7.4):**
- Novo comando `quality <pkg> <pct>` em `control.cmd`.
- `apk_apply.sh quality`:
  - Atualiza `state/apk_quality.json`.
  - Se `pkg` esta em foreground (lookup no `status.json`), aplica
    `wm size $(W*pct/100)x$(H*pct/100)` imediato; se `pct=100`, executa
    `wm size reset`.

---

## 6) Modo Auto era estatico

**Sintoma:** "Auto" so escolhia uma vez (na hora do clique) e ficava.

**Causa-raiz:** Sem polling de estado para reavaliar.

**Correcao (v1.7.4):**
- `AutoMode.decide(state)` recebe o estado mais recente do daemon
  (bateria, soc temp). Sempre que o `Repository` recebe um novo
  `status.json` (poll de 2-6s, FSM-aware), o ViewModel reavalia o modo
  efetivo e re-envia `mode_set EFFECTIVE` se mudou.

---

## 7) Encerrar sessao deixava o motor "preso"

**Sintoma:** Voce saia do jogo e o daemon continuava em ENGAGED por
~30s ate timeout natural.

**Causa-raiz:** Sem comando explicito de encerramento da sessao.

**Correcao (v1.7.4):**
- Novo `shutdown`:
  - Zera `apk_state.json`.
  - Se FSM=ENGAGED, transita para COOLDOWN imediato.
- Acionado por: botao "Recoil" da notificacao (`NotifActionReceiver`),
  `ShutdownReceiver` (boot/desligamento), e quando o tracking de sessao
  do servico detecta fim da sessao.

---

## 8) Reaplicar modo no boot

**Sintoma:** Reiniciava o aparelho e o app voltava para "Balanced"
independente do que estava configurado.

**Causa-raiz:** `BootReceiver` startava o servico mas nao reaplicava
o modo salvo.

**Correcao (v1.7.4):**
- `BootReceiver` chama `repo.reapplyAfterBoot()` que delega a
  `ModeApplier.applyGlobal(prefs.globalMode)`. O `user.conf` e
  regerado e o daemon recarrega.

---

## O que continua sendo limitacao conhecida (nao bug)

- **`wm size` precisa de root e do jogo em foreground.** Se o usuario
  tenta mexer no slider com o jogo fechado, a v1.7.4 apenas registra o
  valor; ele e aplicado na proxima abertura via launcher do APK.
- **Permissao de overlay nao e usada.** O APK nao desenha por cima do
  jogo (escolha de design: zero interferencia visual).
- **Modo "Performance" pode esquentar.** E o trade-off: aumentamos o
  `SOC_TARGET_C10` para 83.0 C, o que deixa o motor mais permissivo. O
  daemon ainda respeita o `BATTERY_EMERGENCY_C10` (entra em SAFE em
  qualquer cenario).
