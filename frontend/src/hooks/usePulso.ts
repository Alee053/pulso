import { useMemo, useState } from 'react'
import { interestOptions } from '../data/interests'
import { mockStartups } from '../data/mockStartups'
import type { AppView, Decision, InterestId, Startup } from '../types/startup'
import {
  adjustPreferenceWeights,
  getActiveSignals,
  getRecommendationReason,
  rankStartups,
  type PreferenceWeights,
} from '../utils/recommendations'

export function usePulso() {
  const [selectedInterests, setSelectedInterests] = useState<InterestId[]>([])
  const [hasStarted, setHasStarted] = useState(false)
  const [activeView, setActiveView] = useState<AppView>('discover')
  const [feed, setFeed] = useState<Startup[]>([])
  const [savedIds, setSavedIds] = useState<string[]>([])
  const [interestedIds, setInterestedIds] = useState<string[]>([])
  const [preferenceWeights, setPreferenceWeights] = useState<PreferenceWeights>({})

  const currentStartup = feed[0]
  const savedStartups = useMemo(
    () => mockStartups.filter((startup) => savedIds.includes(startup.id)),
    [savedIds],
  )
  const interestedStartups = useMemo(
    () => mockStartups.filter((startup) => interestedIds.includes(startup.id)),
    [interestedIds],
  )
  const activeSignals = useMemo(
    () => getActiveSignals(selectedInterests),
    [selectedInterests],
  )

  function toggleInterest(interest: InterestId) {
    setSelectedInterests((current) =>
      current.includes(interest)
        ? current.filter((item) => item !== interest)
        : [...current, interest],
    )
  }

  function startDiscovery() {
    setFeed(rankStartups(mockStartups, selectedInterests))
    setHasStarted(true)
    setActiveView('discover')
  }

  function restartDiscovery() {
    setFeed(rankStartups(mockStartups, selectedInterests, preferenceWeights))
    setActiveView('discover')
  }

  function toggleSaved(startupId: string) {
    setSavedIds((current) =>
      current.includes(startupId)
        ? current.filter((id) => id !== startupId)
        : [...current, startupId],
    )
  }

  function markInterested(startupId: string) {
    setInterestedIds((current) =>
      current.includes(startupId) ? current : [...current, startupId],
    )
  }

  function makeDecision(decision: Decision) {
    if (!currentStartup) {
      return
    }

    const nextWeights = adjustPreferenceWeights(
      currentStartup,
      decision,
      preferenceWeights,
    )

    if (decision === 'interested') {
      markInterested(currentStartup.id)
    }

    setPreferenceWeights(nextWeights)
    setFeed((current) =>
      rankStartups(
        current.filter((startup) => startup.id !== currentStartup.id),
        selectedInterests,
        nextWeights,
      ),
    )
  }

  function goToView(view: AppView) {
    setActiveView(view)
  }

  function getReason(startup: Startup) {
    return getRecommendationReason(startup, selectedInterests, preferenceWeights)
  }

  return {
    activeView,
    activeSignals,
    currentStartup,
    feed,
    getReason,
    goToView,
    hasStarted,
    interestedIds,
    interestedStartups,
    interestOptions,
    makeDecision,
    mockStartups,
    restartDiscovery,
    savedIds,
    savedStartups,
    selectedInterests,
    startDiscovery,
    toggleInterest,
    toggleSaved,
  }
}
