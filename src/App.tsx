import { useState } from 'react'
import { DiscoveryView } from './components/DiscoveryView'
import { NavigationBar } from './components/NavigationBar'
import { Onboarding } from './components/Onboarding'
import { ProfileView } from './components/ProfileView'
import { SavedView } from './components/SavedView'
import { usePulso } from './hooks/usePulso'

function App() {
  const pulso = usePulso()
  const [selectedStartupId, setSelectedStartupId] = useState<string | null>(null)

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
        currentStartup={pulso.currentStartup}
        feedRemaining={pulso.feed.length}
        totalStartups={pulso.mockStartups.length}
        savedIds={pulso.savedIds}
        onDecision={pulso.makeDecision}
        onRestart={pulso.restartDiscovery}
        onSave={pulso.toggleSaved}
        onSelectStartup={(startup) => setSelectedStartupId(startup.id)}
      />
    ),
    saved: (
      <SavedView
        savedStartups={pulso.savedStartups}
        interestedStartups={pulso.interestedStartups}
        onSelectStartup={(startup) => setSelectedStartupId(startup.id)}
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
          setSelectedStartupId(null)
          pulso.goToView(view)
        }}
      />
      {activeContent}
      {selectedStartupId && (
        <span className="sr-only">Proyecto seleccionado: {selectedStartupId}</span>
      )}
    </div>
  )
}

export default App
