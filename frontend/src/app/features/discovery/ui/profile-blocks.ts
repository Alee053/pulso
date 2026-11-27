import { Component, DestroyRef, inject, signal } from '@angular/core'
import { takeUntilDestroyed } from '@angular/core/rxjs-interop'
import { IconComponent } from '../../../shared/icon/icon'
import { commonStrings } from '../../../shared/strings/common.strings'
import { ProfileBlockApi, type BlockedProfile } from '../data-access/profile-block-api'
import { profileBlockStrings } from '../strings/profile-block.strings'

@Component({
  selector: 'pulso-profile-blocks',
  imports: [IconComponent],
  templateUrl: './profile-blocks.html',
  styleUrl: './profile-blocks.css',
})
export class ProfileBlocksComponent {
  protected readonly strings = profileBlockStrings
  protected readonly commonStrings = commonStrings
  protected readonly profiles = signal<BlockedProfile[]>([])
  protected readonly hasMore = signal(false)
  protected readonly loaded = signal(false)
  protected readonly loading = signal(false)
  protected readonly loadFailed = signal(false)
  protected readonly unblocking = signal<string | null>(null)
  protected readonly unblockStatus = signal<'idle' | 'success' | 'error'>('idle')

  private readonly api = inject(ProfileBlockApi)
  private readonly destroyRef = inject(DestroyRef)

  protected loadFirstPage(): void {
    if (!this.loaded()) this.loadMore()
  }

  protected loadMore(): void {
    if (this.loading() || this.unblocking()) return

    this.loading.set(true)
    this.loadFailed.set(false)
    this.api.list(this.profiles().length)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.profiles.update((profiles) => [...profiles, ...page.data])
          this.hasMore.set(page.meta.hasMore)
          this.loaded.set(true)
          this.loading.set(false)
        },
        error: () => {
          this.loadFailed.set(true)
          this.loading.set(false)
        },
      })
  }

  protected unblock(profileId: string, trigger: HTMLElement): void {
    if (this.unblocking() || this.loading()) return

    trigger.focus()
    this.unblocking.set(profileId)
    this.unblockStatus.set('idle')
    this.api.unblockProfile(profileId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.profiles.update((profiles) => profiles.filter((profile) => profile.profileId !== profileId))
          this.unblockStatus.set('success')
          this.unblocking.set(null)
        },
        error: () => {
          this.unblockStatus.set('error')
          this.unblocking.set(null)
        },
      })
  }
}
