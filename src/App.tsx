function App() {
  return (
    <main className="relative min-h-screen overflow-hidden px-5 py-8 text-white sm:px-10 sm:py-12">
      <div className="ambient-orb ambient-orb--mint" aria-hidden="true" />
      <div className="ambient-orb ambient-orb--blue" aria-hidden="true" />
      <section className="glass-panel mx-auto flex min-h-[70vh] max-w-3xl flex-col justify-center p-8 sm:p-14">
        <span className="eyebrow">PULSO / BOLIVIA</span>
        <h1 className="mt-5 max-w-xl text-4xl font-medium tracking-[-0.055em] text-balance sm:text-6xl">
          Descubre lo que está creciendo.
        </h1>
        <p className="mt-6 max-w-lg text-base leading-7 text-white/60 sm:text-lg">
          Un espacio para encontrar ideas, productos y proyectos con la energía
          de lo que viene.
        </p>
      </section>
    </main>
  )
}

export default App
