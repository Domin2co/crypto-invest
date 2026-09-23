import { useState } from 'react'
import type { FormEvent } from 'react'

type Props = { token: string | null }

/** 거래소 자격증명은 제출 직후 입력란을 비우며 브라우저 저장소에 보관하지 않는다. */
export default function ExchangeAccountPanel({ token }: Props) {
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token) return
    setBusy(true); setMessage('')
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    try {
      const response = await fetch('/api/exchange-accounts', { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({
        exchange: form.get('exchange'), accessKey: form.get('accessKey'), secretKey: form.get('secretKey'),
      }) })
      if (!response.ok) throw new Error('exchange-account')
      formElement.reset(); setMessage('거래소 연동 정보를 암호화해 저장했습니다. 키 값은 다시 표시하지 않습니다.')
    } catch { setMessage('연동 정보를 저장하지 못했습니다. 입력값과 로그인 상태를 확인해 주세요.') } finally { setBusy(false) }
  }

  return <section className="mt-6 rounded-xl border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="exchange-account-heading">
    <h2 className="text-xl font-bold text-slate-950" id="exchange-account-heading">거래소 읽기 전용 연동</h2>
    <p className="mt-1 text-sm text-slate-600">자산 조회 권한만 가진 키를 사용하세요. 출금 권한은 허용하지 않습니다.</p>
    {!token ? <p className="mt-4 rounded-lg bg-slate-50 p-3 text-sm text-slate-600">로그인 후 본인 거래소 연동 정보를 저장할 수 있습니다.</p> : <form className="mt-5 grid gap-4 sm:max-w-xl" onSubmit={save}>
      <label className="grid gap-1 text-sm font-medium">거래소<select className="rounded-lg border border-slate-300 bg-slate-50 px-3 py-2 text-slate-950" defaultValue="UPBIT" name="exchange"><option value="UPBIT">Upbit</option><option value="BITHUMB">Bithumb</option></select></label>
      <label className="grid gap-1 text-sm font-medium">접근 키<input className="rounded-lg border border-slate-300 bg-slate-50 px-3 py-2 text-slate-950" autoComplete="off" maxLength={512} name="accessKey" required type="password" /></label>
      <label className="grid gap-1 text-sm font-medium">비밀 키<input className="rounded-lg border border-slate-300 bg-slate-50 px-3 py-2 text-slate-950" autoComplete="off" maxLength={512} name="secretKey" required type="password" /></label>
      <button className="w-full rounded-lg border border-blue-600 px-4 py-2.5 font-bold text-blue-700 disabled:opacity-50 sm:w-fit" disabled={busy} type="submit">암호화 저장</button>
    </form>}
    {message && <p className="mt-4 text-sm text-blue-700" role="status">{message}</p>}
  </section>
}
