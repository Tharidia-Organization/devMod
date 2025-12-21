#!/bin/bash
# DEPRECATED: archived during docs reorganization; kept for historical reference.
#
# Arena Template Rework - Tmux Agent Runner
# Avvia agent in sessioni tmux separate per monitoraggio visivo
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SESSION="arena-agents"

# Verifica tmux
if ! command -v tmux &> /dev/null; then
    echo "tmux non trovato. Installa con: brew install tmux"
    exit 1
fi

# Verifica claude
if ! command -v claude &> /dev/null; then
    echo "Claude CLI non trovato"
    exit 1
fi

# Kill sessione esistente
tmux kill-session -t $SESSION 2>/dev/null || true

# Prompt template per ogni agent
agent_prompt() {
    local num=$1
    local file=$(ls ${SCRIPT_DIR}/TODO_AGENT_${num}_*.md 2>/dev/null | head -1)
    echo "Leggi ${file} e implementa tutti i task. Quando finito, crea TODO_AGENT_${num}_COMPLETE.md"
}

# ============================================================
# FASE 1: 6 agent in parallelo
# ============================================================
echo "Avvio Fase 1: Agent 01, 05, 07, 08, 09, 10"

tmux new-session -d -s $SESSION -n "agent-01" "cd ${SCRIPT_DIR} && claude '$(agent_prompt 01)'; read"
tmux new-window -t $SESSION -n "agent-05" "cd ${SCRIPT_DIR} && claude '$(agent_prompt 05)'; read"
tmux new-window -t $SESSION -n "agent-07" "cd ${SCRIPT_DIR} && claude '$(agent_prompt 07)'; read"
tmux new-window -t $SESSION -n "agent-08" "cd ${SCRIPT_DIR} && claude '$(agent_prompt 08)'; read"
tmux new-window -t $SESSION -n "agent-09" "cd ${SCRIPT_DIR} && claude '$(agent_prompt 09)'; read"
tmux new-window -t $SESSION -n "agent-10" "cd ${SCRIPT_DIR} && claude '$(agent_prompt 10)'; read"

echo ""
echo "============================================"
echo "Sessione tmux '${SESSION}' creata con 6 agent"
echo ""
echo "Comandi utili:"
echo "  tmux attach -t ${SESSION}     # Connettiti alla sessione"
echo "  Ctrl+B, N                     # Prossima finestra"
echo "  Ctrl+B, P                     # Finestra precedente"
echo "  Ctrl+B, W                     # Lista finestre"
echo "  Ctrl+B, D                     # Disconnetti (agent continuano)"
echo ""
echo "Quando Fase 1 completa, esegui:"
echo "  $0 phase2"
echo "============================================"

# Opzionale: connetti subito
# tmux attach -t $SESSION

case "${1:-phase1}" in
    phase1)
        # Già fatto sopra
        ;;
    phase2)
        echo "Avvio Fase 2: Agent 02, 06"
        tmux new-window -t $SESSION -n "agent-02" "cd ${SCRIPT_DIR} && claude '$(agent_prompt 02)'; read"
        tmux new-window -t $SESSION -n "agent-06" "cd ${SCRIPT_DIR} && claude '$(agent_prompt 06)'; read"
        echo "Agent 02 e 06 avviati. Quando completi, esegui: $0 phase3"
        ;;
    phase3)
        echo "Avvio Fase 3: Agent 03"
        tmux new-window -t $SESSION -n "agent-03" "cd ${SCRIPT_DIR} && claude '$(agent_prompt 03)'; read"
        echo "Agent 03 avviato. Quando completo, esegui: $0 phase4"
        ;;
    phase4)
        echo "Avvio Fase 4: Agent 04"
        tmux new-window -t $SESSION -n "agent-04" "cd ${SCRIPT_DIR} && claude '$(agent_prompt 04)'; read"
        echo "Agent 04 avviato. Quando completo, esegui: $0 phase5"
        ;;
    phase5)
        echo "Avvio Fase 5: Agent 11"
        tmux new-window -t $SESSION -n "agent-11" "cd ${SCRIPT_DIR} && claude '$(agent_prompt 11)'; read"
        echo "Agent 11 avviato. Quando completo, esegui: $0 phase6"
        ;;
    phase6)
        echo "Avvio Fase 6: Agent 12 (Integrazione Finale)"
        tmux new-window -t $SESSION -n "agent-12" "cd ${SCRIPT_DIR} && claude '$(agent_prompt 12)'; read"
        echo "Agent 12 avviato. Questo è l'ultimo!"
        ;;
    status)
        echo "File di completamento:"
        ls -la ${SCRIPT_DIR}/TODO_AGENT_*_COMPLETE.md 2>/dev/null || echo "Nessun agent completato"
        ;;
    *)
        echo "Usage: $0 [phase1|phase2|phase3|phase4|phase5|phase6|status]"
        ;;
esac
