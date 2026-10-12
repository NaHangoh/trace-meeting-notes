import { ScriptInputPage } from './features/input/ScriptInputPage'
import { JobStatusPage } from './features/job/JobStatusPage'
import { jobHash, useRoute } from './route'

/** 입력 화면(#/)과 작업 상태 화면(#/jobs/{id}). 작업을 만들면 상태 화면으로 이동한다. */
export default function App() {
  const route = useRoute()
  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-4 p-4">
      <h1 className="text-xl font-bold">회의 정리 노트</h1>
      {route.page === 'job' ? (
        // 작업이 바뀌면 화면 상태(다시 시도 등)를 새로 시작한다
        <JobStatusPage key={route.jobId ?? 'invalid'} jobId={route.jobId} />
      ) : (
        <ScriptInputPage
          onJobCreated={(jobId) => {
            window.location.hash = jobHash(jobId)
          }}
        />
      )}
    </main>
  )
}
