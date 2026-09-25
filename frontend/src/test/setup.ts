import { afterAll, afterEach, beforeAll } from 'vitest'
import { enableAutoUnmount } from '@vue/test-utils'

import { server } from '@/mocks/node'
import { resetMockSession } from '@/mocks/handlers'

// Every test runs against the MSW handlers. A request without a handler fails the test.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
// Drop handlers a test added with server.use(...), so tests don't leak into each other
afterEach(() => {
  server.resetHandlers()
  resetMockSession()
})
afterAll(() => server.close())
// Unmount every component after its test, so nothing stays mounted on the shared router
enableAutoUnmount(afterEach)

// jsdom has no showModal()/close() on <dialog> yet. A small stand-in with the same observable
// behaviour: `open` toggles, and close() fires the `close` event that BaseDialog listens to.
if (!HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.open = true
  }
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    this.open = false
    this.dispatchEvent(new Event('close'))
  }
}
