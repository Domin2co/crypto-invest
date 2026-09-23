import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'

type Consent = { type: string; policyVersion: string; grantedAt: string; withdrawnAt: string | null }
type PrivacyExport = { email: string; consents: Consent[] }
type Props = { token: string | null; onTokenChange: (token: string | null) => void }
type Mode = 'login' | 'register'

/** 토큰은 메모리에만 두고, 로그인과 가입 모두 서버 API로 처리한다. */
export default function AccountAccess({ token, onTokenChange }: Props) {
  const [privacy, setPrivacy] = useState<PrivacyExport | null>(null)
  const [mode, setMode] = useState<Mode>('login')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!token) { setPrivacy(null); return }
    let active = true
    fetch('/api/privacy/me', { headers: { Authorization: `Bearer ${token}` } })
      .then((response) => response.ok ? response.json() as Promise<PrivacyExport> : Promise.reject(new Error('privacy')))
      .then((data) => { if (active) setPrivacy(data) })
      .catch(() => { if (active) setMessage('계정 정보를 불러오지 못했습니다.') })
    return () => { active = false }
  }, [token])

  async function authenticate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setMessage('')
    const form = new FormData(event.currentTarget)
    const register = mode === 'register'
    try {
      const response = await fetch(`/api/auth/${register ? 'register' : 'login'}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
          email: form.get('email'), password: form.get('password'),
          ...(register ? { privacyAccepted: form.get('privacyAccepted') === 'on', marketingAccepted: form.get('marketingAccepted') === 'on' } : {}),
        }),
      })
      if (!response.ok) {
        const problem = await response.json().catch(() => ({ code: '' })) as { code?: string }
        if (response.status === 409 || problem.code === 'INVALID_STATE') throw new Error('server-state')
        throw new Error(register ? 'register-input' : 'login-input')
      }
      const accessToken = (await response.json() as { accessToken: string }).accessToken
      onTokenChange(accessToken)
      setMessage(register ? '회원가입이 완료되었습니다.' : '로그인되었습니다.')
    } catch (error) {
      const reason = error instanceof Error ? error.message : ''
      setMessage(reason === 'server-state'
        ? '서버 설정 또는 서비스 상태 문제로 처리하지 못했습니다. 백엔드 상태와 로그를 확인해 주세요.'
        : reason === 'register-input'
          ? '가입하지 못했습니다. 이메일 형식, 12자 이상 비밀번호, 필수 개인정보 동의와 기존 가입 여부를 확인해 주세요.'
          : reason === 'login-input'
            ? '로그인하지 못했습니다. 이메일과 비밀번호를 확인해 주세요.'
            : '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    } finally { setBusy(false) }
  }

  async function marketing(accepted: boolean) {
    if (!token) return
    setBusy(true); setMessage('')
    try {
      const response = await fetch('/api/privacy/marketing-consent', { method: 'PATCH', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ accepted }) })
      if (!response.ok) throw new Error('marketing')
      const refresh = await fetch('/api/privacy/me', { headers: { Authorization: `Bearer ${token}` } })
      if (!refresh.ok) throw new Error('privacy')
      setPrivacy(await refresh.json() as PrivacyExport); setMessage(accepted ? '마케팅 수신에 동의했습니다.' : '마케팅 수신 동의를 철회했습니다.')
    } catch { setMessage('동의 상태를 변경하지 못했습니다.') } finally { setBusy(false) }
  }

  async function deleteAccount() {
    if (!token || !window.confirm('계정을 삭제하면 거래소 API 키가 삭제되고 로그아웃됩니다. 계속할까요?')) return
    setBusy(true); setMessage('')
    try {
      const response = await fetch('/api/privacy/me', { method: 'DELETE', headers: { Authorization: `Bearer ${token}` } })
      if (!response.ok) throw new Error('delete')
      onTokenChange(null); setPrivacy(null); setMessage('계정이 삭제되었습니다.')
    } catch { setMessage('계정을 삭제하지 못했습니다.') } finally { setBusy(false) }
  }

  const marketingConsent = privacy?.consents.find((consent) => consent.type === 'MARKETING' && !consent.withdrawnAt)
  return <section className="panel-card" aria-labelledby="account-heading">
    <p className="eyebrow">계정</p><h2 className="panel-title" id="account-heading">{token ? '내 계정' : mode === 'login' ? '다시 오신 것을 환영해요' : '계정을 만들어 보세요'}</h2>
    <p className="panel-description">비밀번호는 안전하게 암호화해 저장하고, 로그인 토큰은 브라우저 메모리에만 보관합니다.</p>
    {!token ? <>
      <div className="mt-6 flex gap-2 border-b border-slate-200" role="tablist" aria-label="계정 작업">
        <button className={`px-4 py-3 text-sm font-semibold ${mode === 'login' ? 'border-b-2 border-blue-600 text-blue-700' : 'text-slate-500'}`} aria-selected={mode === 'login'} onClick={() => { setMode('login'); setMessage('') }} role="tab" type="button">로그인</button>
        <button className={`px-4 py-3 text-sm font-semibold ${mode === 'register' ? 'border-b-2 border-blue-600 text-blue-700' : 'text-slate-500'}`} aria-selected={mode === 'register'} onClick={() => { setMode('register'); setMessage('') }} role="tab" type="button">회원가입</button>
      </div>
      <form className="mt-6 grid max-w-xl gap-4" onSubmit={authenticate}>
        <label className="field-label">이메일<input className="field-input" name="email" type="email" autoComplete="email" required maxLength={254} /></label>
        <label className="field-label">비밀번호<input className="field-input" name="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={12} maxLength={128} /></label>
        {mode === 'register' && <>
          <label className="flex items-start gap-3 text-sm leading-6"><input className="mt-1 size-4" name="privacyAccepted" type="checkbox" required /><span><a className="text-blue-700 underline" href="/privacy" target="_blank" rel="noreferrer">개인정보 처리방침</a>을 읽고 개인정보 처리에 동의합니다. (필수)</span></label>
          <label className="flex items-start gap-3 text-sm leading-6"><input className="mt-1 size-4" name="marketingAccepted" type="checkbox" /><span>서비스 소식과 마케팅 정보 수신에 동의합니다. (선택)</span></label>
        </>}
        <button className="primary-button mt-2 w-full sm:w-fit" disabled={busy} type="submit">{busy ? '처리 중...' : mode === 'login' ? '로그인' : '회원가입'}</button>
      </form>
    </> : <div className="mt-6 grid max-w-xl gap-4">
      <p className="rounded-xl bg-slate-50 p-4 text-sm" aria-live="polite">로그인 계정: {privacy?.email ?? '정보를 불러오는 중입니다.'}</p>
      <div className="flex flex-col gap-3 sm:flex-row"><button className="secondary-button" disabled={busy} onClick={() => marketing(!marketingConsent)} type="button">{marketingConsent ? '마케팅 수신 철회' : '마케팅 수신 동의'}</button><button className="danger-button" disabled={busy} onClick={deleteAccount} type="button">계정 삭제</button><button className="secondary-button" onClick={() => { onTokenChange(null); setMessage('로그아웃되었습니다.') }} type="button">로그아웃</button></div>
      <p className="text-sm text-slate-500">계정 삭제 시 거래소 API key를 삭제하고 직접 식별정보를 익명화합니다.</p>
    </div>}
    {message && <p className="mt-4 text-sm text-blue-800" role="status">{message}</p>}
  </section>
}