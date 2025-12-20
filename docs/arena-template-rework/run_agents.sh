#!/bin/bash
#
# Arena Template Rework - Parallel Agent Runner
# Esegue 12 agent Claude in parallelo rispettando le dipendenze
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${SCRIPT_DIR}/logs"
COMPLETE_DIR="${SCRIPT_DIR}"

# Colori
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

mkdir -p "$LOG_DIR"

log() {
    echo -e "${GREEN}[$(date '+%H:%M:%S')]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[$(date '+%H:%M:%S')] WARNING:${NC} $1"
}

error() {
    echo -e "${RED}[$(date '+%H:%M:%S')] ERROR:${NC} $1"
}

# Verifica che claude sia installato
if ! command -v claude &> /dev/null; then
    error "Claude CLI non trovato. Installa con: npm install -g @anthropic-ai/claude-code"
    exit 1
fi

run_agent() {
    local agent_num=$1
    local agent_file=$(ls ${SCRIPT_DIR}/TODO_AGENT_${agent_num}_*.md 2>/dev/null | head -1)

    if [ -z "$agent_file" ]; then
        error "File agent ${agent_num} non trovato"
        return 1
    fi

    local agent_name=$(basename "$agent_file" .md)
    local log_file="${LOG_DIR}/${agent_name}.log"

    log "Avvio Agent ${agent_num}: ${agent_name}"

    # Esegui claude con il file TODO come contesto
    # --print per output non-interattivo
    claude --print "Leggi il file ${agent_file} e implementa TUTTI i task elencati. Crea i file Java necessari. Quando hai finito, crea il file ${COMPLETE_DIR}/TODO_AGENT_${agent_num}_COMPLETE.md con il summary delle modifiche." \
        > "$log_file" 2>&1 &

    echo $!  # Ritorna PID
}

wait_for_completion() {
    local agent_num=$1
    local complete_file="${COMPLETE_DIR}/TODO_AGENT_${agent_num}_COMPLETE.md"
    local timeout=3600  # 1 ora max per agent
    local elapsed=0

    while [ ! -f "$complete_file" ] && [ $elapsed -lt $timeout ]; do
        sleep 30
        elapsed=$((elapsed + 30))
        if [ $((elapsed % 300)) -eq 0 ]; then
            log "Agent ${agent_num}: in attesa... (${elapsed}s)"
        fi
    done

    if [ -f "$complete_file" ]; then
        log "Agent ${agent_num}: COMPLETATO"
        return 0
    else
        warn "Agent ${agent_num}: TIMEOUT dopo ${timeout}s"
        return 1
    fi
}

is_complete() {
    local agent_num=$1
    [ -f "${COMPLETE_DIR}/TODO_AGENT_${agent_num}_COMPLETE.md" ]
}

# ============================================================
# FASE 1: Agent senza dipendenze (parallelo)
# ============================================================
phase1() {
    log "========== FASE 1: Avvio agent senza dipendenze =========="

    local pids=()

    for agent in 01 05 07 08 09 10; do
        if ! is_complete "$agent"; then
            run_agent "$agent"
            pids+=($!)
        else
            log "Agent ${agent} già completato, skip"
        fi
    done

    # Attendi tutti i PID
    for pid in "${pids[@]}"; do
        wait $pid 2>/dev/null || true
    done

    # Verifica completamento
    for agent in 01 05 07 08 09 10; do
        wait_for_completion "$agent" || warn "Agent ${agent} non completato"
    done

    log "Fase 1 completata"
}

# ============================================================
# FASE 2: Agent che dipendono da Fase 1
# ============================================================
phase2() {
    log "========== FASE 2: Agent con dipendenze da Fase 1 =========="

    # Agent 02 dipende da Agent 01
    if is_complete "01" && ! is_complete "02"; then
        run_agent "02"
    fi

    # Agent 06 dipende da Agent 05
    if is_complete "05" && ! is_complete "06"; then
        run_agent "06"
    fi

    wait_for_completion "02" || warn "Agent 02 non completato"
    wait_for_completion "06" || warn "Agent 06 non completato"

    log "Fase 2 completata"
}

# ============================================================
# FASE 3: Agent 03 (dipende da Agent 02)
# ============================================================
phase3() {
    log "========== FASE 3: Agent 03 =========="

    if is_complete "02" && ! is_complete "03"; then
        run_agent "03"
        wait_for_completion "03" || warn "Agent 03 non completato"
    fi

    log "Fase 3 completata"
}

# ============================================================
# FASE 4: Agent 04 (dipende da Agent 02, 03)
# ============================================================
phase4() {
    log "========== FASE 4: Agent 04 =========="

    if is_complete "02" && is_complete "03" && ! is_complete "04"; then
        run_agent "04"
        wait_for_completion "04" || warn "Agent 04 non completato"
    fi

    log "Fase 4 completata"
}

# ============================================================
# FASE 5: Agent 11 (dipende da Agent 04, 05, 06)
# ============================================================
phase5() {
    log "========== FASE 5: Agent 11 =========="

    if is_complete "04" && is_complete "05" && is_complete "06" && ! is_complete "11"; then
        run_agent "11"
        wait_for_completion "11" || warn "Agent 11 non completato"
    fi

    log "Fase 5 completata"
}

# ============================================================
# FASE 6: Agent 12 - Integrazione finale (dipende da TUTTI)
# ============================================================
phase6() {
    log "========== FASE 6: Agent 12 - Integrazione Finale =========="

    local all_complete=true
    for agent in 01 02 03 04 05 06 07 08 09 10 11; do
        if ! is_complete "$agent"; then
            all_complete=false
            warn "Agent ${agent} non completato, impossibile avviare Agent 12"
        fi
    done

    if $all_complete && ! is_complete "12"; then
        run_agent "12"
        wait_for_completion "12" || warn "Agent 12 non completato"
    fi

    log "Fase 6 completata"
}

# ============================================================
# REPORT FINALE
# ============================================================
final_report() {
    log "========== REPORT FINALE =========="

    local completed=0
    local failed=0

    for agent in 01 02 03 04 05 06 07 08 09 10 11 12; do
        if is_complete "$agent"; then
            echo -e "  Agent ${agent}: ${GREEN}COMPLETATO${NC}"
            ((completed++))
        else
            echo -e "  Agent ${agent}: ${RED}NON COMPLETATO${NC}"
            ((failed++))
        fi
    done

    echo ""
    log "Completati: ${completed}/12"

    if [ $failed -eq 0 ]; then
        log "${GREEN}TUTTI GLI AGENT COMPLETATI CON SUCCESSO${NC}"
    else
        warn "${failed} agent non completati. Controlla i log in ${LOG_DIR}"
    fi
}

# ============================================================
# MAIN
# ============================================================
main() {
    log "Arena Template Rework - Avvio orchestrazione"
    log "Log directory: ${LOG_DIR}"

    phase1
    phase2
    phase3
    phase4
    phase5
    phase6

    final_report
}

# Gestione argomenti
case "${1:-all}" in
    phase1) phase1 ;;
    phase2) phase2 ;;
    phase3) phase3 ;;
    phase4) phase4 ;;
    phase5) phase5 ;;
    phase6) phase6 ;;
    status) final_report ;;
    all) main ;;
    *)
        echo "Usage: $0 [phase1|phase2|phase3|phase4|phase5|phase6|status|all]"
        exit 1
        ;;
esac
