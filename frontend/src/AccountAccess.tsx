import { useState } from 'react'
import type { FormEvent } from 'react'

type Consent = { type: string; policyVersion: string; grantedAt: string; withdrawnAt: string | null }
type PrivacyExport = { email: string; consents: Consent[] }

/** 토큰은 브라우저 저장소에 쓰지 않고, 가입·권리 요청이 끝나는 동안만 메모리에 둔다. */
export default function AccountAccess() {
  const [token, setToken] = useState<string | null>(null)
  const [privacy, setPrivacy] = useState<PrivacyExport | null>(null)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  async function loadPrivacy(accessToken: string) {
    const response = await fetch('/api/privacy/me', { headers: { Authorization: `Bearer ${accessToken}` } })
    if (!response.ok) throw new Error('privacy')
    setPrivacy(await response.json() as PrivacyExport)
  }

  async function register(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setMessage('')
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    try {
      const response = await fetch('/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
        email: form.get('email'), password: form.get('password'), privacyAccepted: form.get('privacyAccepted') === 'on', marketingAccepted: form.get('marketingAccepted') === 'on',
      }) })
      if (!response.ok) throw new Error('register')
      const accessToken = (await response.json() as { accessToken: string }).accessToken
      formElement.reset(); setToken(accessToken); await loadPrivacy(accessToken); setMessage('가입과 필수 개인정보 처리 동의가 완료되었습니다.')
    } catch { setMessage('가입을 완료하지 못했습니다. 입력값과 네트워크를 확인해 주세요.') } finally { setBusy(false) }
  }

  async function marketing(accepted: boolean) {
    if (!token) return
    setBusy(true); setMessage('')
    try {
      const response = await fetch('/api/privacy/marketing-consent', { method: 'PATCH', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ accepted }) })
      if (!response.ok) throw new Error('marketing')
      await loadPrivacy(token); setMessage(accepted ? '마케팅 수신에 동의했습니다.' : '마케팅 수신 동의를 철회했습니다.')
    } catch { setMessage('동의 상태를 변경하지 못했습니다.') } finally { setBusy(false) }
  }

  async function deleteAccount() {
    if (!token || !window.confirm('계정을 삭제하면 거래소 API key가 삭제되고 로그인할 수 없습니다. 계속할까요?')) return
    setBusy(true); setMessage('')
    try {
      const response = await fetch('/api/privacy/me', { method: 'DELETE', headers: { Authorization: `Bearer ${token}` } })
      if (!response.ok) throw new Error('delete')
      setToken(null); setPrivacy(null); setMessage('계정을 삭제했습니다.')
    } catch { setMessage('계정을 삭제하지 못했습니다.') } finally { setBusy(false) }
  }

  const marketingConsent = privacy?.consents.find((consent) => consent.type === 'MARKETING' && !consent.withdrawnAt)
  return <section className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-5" id="account" aria-labelledby="account-heading">
    <h2 className="text-xl font-bold text-white" id="account-heading">회원가입과 개인정보</h2>
    <p className="mt-1 text-sm text-slate-400">API key와 비밀번호는 화면·브라우저 저장소에 보관하지 않습니다.</p>
    {!token ? <form className="mt-5 grid gap-4 sm:max-w-xl" onSubmit={register}>
      <label className="grid gap-1 text-sm font-medium">이메일<input className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" name="email" type="email" autoComplete="email" required maxLength={254} /></label>
      <label className="grid gap-1 text-sm font-medium">비밀번호<input className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-white" name="password" type="password" autoComplete="new-password" required minLength={12} maxLength={128} /></label>
      <label className="flex items-start gap-3 text-sm"><input className="mt-1 size-4" name="privacyAccepted" type="checkbox" required /><span><a className="text-cyan-300 underline" href="/privacy-policy.html" target="_blank" rel="noreferrer">개인정보처리방침</a>을 읽고 개인정보 처리에 동의합니다. (필수)</span></label>
      <label className="flex items-start gap-3 text-sm"><input className="mt-1 size-4" name="marketingAccepted" type="checkbox" /><span>서비스 소식과 마케팅 정보 수신에 동의합니다. (선택, 언제든 철회 가능)</span></label>
      <button className="w-full rounded-lg bg-cyan-400 px-4 py-2.5 font-bold text-slate-950 disabled:opacity-50 sm:w-fit" disabled={busy} type="submit">회원가입</button>
    </form> : <div className="mt-5 grid gap-4 sm:max-w-xl">
      <p className="rounded-lg bg-slate-950 p-3 text-sm" aria-live="polite">로그인 계정: {privacy?.email ?? '정보를 불러오는 중'}</p>
      <div className="flex flex-col gap-3 sm:flex-row"><button className="rounded-lg border border-slate-600 px-4 py-2 text-sm font-bold" disabled={busy} onClick={() => marketing(!marketingConsent)} type="button">{marketingConsent ? '마케팅 수신 철회' : '마케팅 수신 동의'}</button><button className="rounded-lg border border-rose-500/70 px-4 py-2 text-sm font-bold text-rose-200" disabled={busy} onClick={deleteAccount} type="button">계정 삭제</button></div>
      <p className="text-xs text-slate-500">계정 삭제 시 거래소 API key를 삭제하고 직접 식별정보를 익명화합니다. 법령상 보존 대상은 개인정보처리방침을 따릅니다.</p>
    </div>}
    {message && <p className="mt-4 text-sm text-cyan-200" role="status">{message}</p>}
  </section>
}
