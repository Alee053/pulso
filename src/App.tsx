import { AnimatePresence } from 'framer-motion'
import { useCallback, useState } from 'react'
import { DiscoveryView } from './components/DiscoveryView'
import { NavigationBar } from './components/NavigationBar'
import { Onboarding } from './components/Onboarding'
import { ProfileView } from './components/ProfileView'
import { SavedView } from './components/SavedView'
import { StartupDetailModal } from './components/StartupDetailModal'
import { usePulso } from './hooks/usePulso'
import type { Startup } from './types/startup'

function App() {
  const pulso = usePulso()
  const [selectedStartup, setSelectedStartup] = useState<Startup | null>(null)
  const closeStartup = useCallback(() => setSelectedStartup(null), [])

  if (!pulso.hasStarted) {
    return (
      <Onboarding
        interestOptions={pulso.interestOptions}
        selectedInterests={pulso.selectedInterests}
        onToggleInterest={pulso.toggleInterest}
        onStart={pulso.startDiscovery}
      />
    )
  }

  const activeContent = {
    discover: (
      <DiscoveryView
        activeSignals={pulso.activeSignals}
        currentStartup={pulso.currentStartup}
        feedRemaining={pulso.feed.length}
        isModalOpen={selectedStartup !== null}
        totalStartups={pulso.mockStartups.length}
        savedIds={pulso.savedIds}
        onDecision={pulso.makeDecision}
        onRestart={pulso.restartDiscovery}
        onSave={pulso.toggleSaved}
        onSelectStartup={setSelectedStartup}
      />
    ),
    saved: (
      <SavedView
        savedStartups={pulso.savedStartups}
        interestedStartups={pulso.interestedStartups}
        onSelectStartup={setSelectedStartup}
      />
    ),
    profile: (
      <ProfileView
        interestOptions={pulso.interestOptions}
        selectedInterests={pulso.selectedInterests}
      />
    ),
  }[pulso.activeView]

  return (
    <div className="relative flex min-h-screen flex-col overflow-hidden text-white">
      <div className="ambient-orb ambient-orb--mint" aria-hidden="true" />
      <div className="ambient-orb ambient-orb--blue" aria-hidden="true" />
      <NavigationBar
        activeView={pulso.activeView}
        savedCount={pulso.savedIds.length}
        interestedCount={pulso.interestedIds.length}
        onNavigate={(view) => {
          setSelectedStartup(null)
          pulso.goToView(view)
        }}
      />
      {activeContent}
      <AnimatePresence>
        {selectedStartup && (
          <StartupDetailModal
            startup={selectedStartup}
            recommendation={pulso.getReason(selectedStartup)}
            onClose={closeStartup}
          />
        )}
      </AnimatePresence>
    </div>
  )
}

export default App
