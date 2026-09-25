export interface HelloResponse {
  message: string
}

export async function fetchHello(name?: string): Promise<HelloResponse> {
  const query = name ? `?name=${encodeURIComponent(name)}` : ''
  const response = await fetch(`/api/hello${query}`)
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`)
  }
  return (await response.json()) as HelloResponse
}
