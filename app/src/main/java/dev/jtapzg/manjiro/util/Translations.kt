package dev.jtapzg.manjiro.util

/**
 * Vocabulário Tokyo Revengers para o Manjiro Dinamic.
 *
 * O backend (daemon manjirod) usa termos técnicos. A UI **só** traduz aqui.
 * Mantém o app com cara de companion oficial de gamer pro sem expor
 * jargão tipo "Lyapunov", "MPC", "throttle", "actuator", etc.
 */
object Translations {

    /** FSM states do daemon -> nome em PT-BR. */
    fun mode(daemonMode: String?): String = when (daemonMode?.lowercase()) {
        "bootstrap" -> "Reunindo o Toman"
        "idle" -> "Toman em casa"
        "active" -> "Patrulha do bairro"
        "engaged", "engage" -> "Modo Mikey"
        "cooldown" -> "Recuo estratégico"
        "safe", "emergency" -> "Refúgio do Toman"
        else -> "Aguardando o Toman"
    }

    /** Pequeno descritivo do estado, mostrado abaixo do nome. */
    fun modeBlurb(daemonMode: String?): String = when (daemonMode?.lowercase()) {
        "bootstrap" -> "preparando os capitães"
        "idle" -> "tudo em paz no bairro"
        "active" -> "patrulhando o desempenho"
        "engaged", "engage" -> "Mikey desperto"
        "cooldown" -> "recuperando o fôlego"
        "safe", "emergency" -> "protegendo a Hinata"
        else -> "esperando o primeiro relatório"
    }

    /**
     * Última ação tomada pelo motor (do campo `last_action` do status.json).
     *
     * Termos do daemon → frase em PT-BR sem jargão.
     */
    fun action(daemonAction: String?): String = when (daemonAction?.lowercase()) {
        null, "", "hold", "noop" -> "Tudo sob controle"
        "clamp_background" -> "Segurando os recrutas"
        "release_background" -> "Liberando os recrutas"
        "reduce_gpu_micro", "gpu_clamp_down" -> "Adreno respirando"
        "raise_gpu_micro", "gpu_release" -> "Adreno em chamas"
        "reduce_cpu_micro", "cpu_clamp_down" -> "Capitães contidos"
        "raise_cpu_micro", "cpu_release" -> "Capitães livres"
        "limit_charge" -> "Carregando devagar"
        "release_charge" -> "Carregando normal"
        "boost_prime" -> "Mikey acordou"
        "reduce_prime" -> "Mikey descansando"
        "thermal_clamp" -> "Black Dragon contido"
        "thermal_release" -> "Black Dragon longe"
        "safe_enter" -> "Recolhendo o Toman"
        "safe_exit" -> "Saindo do refúgio"
        "cooldown_enter" -> "Hora de recuar"
        "cooldown_exit" -> "Recuperado"
        "engage" -> "Modo Mikey ativado"
        "disengage" -> "Recuando da batalha"
        else -> daemonAction.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /**
     * Atuadores ativos (campo `active_actuators`).
     * Cada nome técnico vira uma "técnica" do Toman.
     */
    fun technique(actuator: String): String = when (actuator.lowercase()) {
        "gpu_devfreq_clamp", "kgsl_clamp" -> "Dragão contido"
        "gpu_micro_release", "kgsl_boost" -> "Dragão em chamas"
        "clamp_background", "psi_background" -> "Recrutas em silêncio"
        "release_background" -> "Recrutas liberados"
        "limit_charge_current", "charge_thermal" -> "Carga sob controle"
        "boost_prime", "schedutil_boost_prime" -> "Ordem direta do Mikey"
        "clamp_prime" -> "Mikey segura o ritmo"
        "boost_gold" -> "Capitães mobilizados"
        "clamp_gold" -> "Capitães em formação"
        "boost_silver" -> "Soldados em corrida"
        "clamp_silver" -> "Soldados em pausa"
        "thermal_clamp_max_freq" -> "Linha vermelha do Mikey"
        "dnd_active" -> "Silêncio em batalha"
        "framerate_lock" -> "Cadência travada"
        "hud_overlay" -> "Visão do líder"
        "input_boost" -> "Reflexo do Mikey"
        "stable_voltage" -> "Bateria fresca"
        "doze_resist" -> "Sentinela acordada"
        "io_priority_game" -> "Estradas livres"
        "memcg_boost" -> "Memória do Toman"
        else -> actuator.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /** Razão da SAFE state (campo `safe_reason`). */
    fun safeReason(reason: String?): String = when (reason?.lowercase()) {
        null, "" -> "Motivo desconhecido"
        "battery_emergency", "battery_overheat" -> "Hinata muito quente"
        "soc_critical", "soc_overheat" -> "Toman exausto"
        "fail_limit_exceeded", "actuator_failures" -> "Falhas seguidas nos comandos"
        "shutdown", "service_stop" -> "Toman recolhendo"
        "user_request" -> "Comando do líder"
        "ipc_starvation" -> "Toman sem fôlego"
        else -> reason.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /** Game class do daemon -> patente no Toman. */
    fun gameClass(daemonClass: String?): String = when (daemonClass?.lowercase()) {
        "heavy_3d", "heavy-3d", "3d_heavy" -> "Capitão 3D"
        "open_world", "open-world" -> "Líder de mundo aberto"
        "competitive", "esports" -> "Competitivo"
        "casual" -> "Casual"
        "benchmark" -> "Benchmark"
        "imported", "auto" -> "Importado"
        "user_added", "user" -> "Recrutado por você"
        else -> daemonClass?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
            ?: "Capitão"
    }

    /** Discipline label baseado em lyapunov (0..10000). Menor = mais disciplinado. */
    fun discipline(lyapunovV: Int): String = when {
        lyapunovV < 0 -> "—"
        lyapunovV < 1500 -> "ótima"
        lyapunovV < 4000 -> "normal"
        lyapunovV < 7000 -> "limitada"
        else -> "instável"
    }
}
