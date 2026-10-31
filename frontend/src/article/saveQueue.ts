/** Every job captures its snapshot when it actually runs, never before the previous response. */
export function createSaveQueue() {
  let tail: Promise<unknown> = Promise.resolve()
  return <T>(job: () => Promise<T>): Promise<T> => {
    const result = tail.then(job)
    tail = result.catch(() => undefined)
    return result
  }
}
