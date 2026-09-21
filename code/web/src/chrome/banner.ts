// A strip along the bottom for something that happened to the file while it was open: it changed
// somewhere else, or it is not there any more. Along the bottom rather than under the top menu,
// which slides away with the scroll and would take the message with it.
//
// It only ever appears for a buffer the user has typed in. A clean one takes the change without
// being asked, which is the whole point of watching the file (plan.md "감시").

/** Accessible names, from Kotlin's string resources like every other text on the page. */
export interface BannerLabels {
  reload: string
  keepMine: string
  dismiss: string
}

export interface Banner {
  /** Offers the change: taking it runs [reload], leaving it alone just closes. */
  ask(message: string, reload: () => void): void
  /** Says something there is nothing to decide about. */
  tell(message: string): void
  hide(): void
  label(labels: BannerLabels): void
}

export function createBanner(): Banner {
  const bar = document.createElement('div')
  bar.id = 'banner'
  bar.hidden = true
  const message = document.createElement('p')
  const actions = document.createElement('div')
  actions.className = 'actions'
  bar.append(message, actions)
  document.body.append(bar)

  let labels: BannerLabels | null = null
  let showing: { text: string; reload: (() => void) | null } | null = null

  const button = (text: string, onClick: () => void) => {
    const element = document.createElement('button')
    element.type = 'button'
    element.textContent = text
    element.addEventListener('click', onClick)
    return element
  }

  function render() {
    // The labels arrive from Kotlin after the page is up, so a banner that beats them waits here
    // rather than showing its buttons in whatever language this file happens to be written in.
    bar.hidden = showing === null || labels === null
    if (!showing || !labels) return
    message.textContent = showing.text
    const reload = showing.reload
    // Keeping what the user typed is the left, unhurried one; taking the change throws it away.
    actions.replaceChildren(
      ...(reload
        ? [button(labels.keepMine, hide), button(labels.reload, () => { hide(); reload() })]
        : [button(labels.dismiss, hide)]),
    )
  }

  function hide() {
    showing = null
    render()
  }

  return {
    ask(text, reload) {
      showing = { text, reload }
      render()
    },
    tell(text) {
      showing = { text, reload: null }
      render()
    },
    hide,
    label(next) {
      labels = next
      render()
    },
  }
}
