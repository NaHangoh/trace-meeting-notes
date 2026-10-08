import { ScriptInputPage } from './features/input/ScriptInputPage'

export default function App() {
  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-4 p-4">
      <h1 className="text-xl font-bold">회의 정리 노트</h1>
      <ScriptInputPage />
    </main>
  )
}
