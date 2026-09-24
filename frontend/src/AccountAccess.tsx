import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import PasswordField from './PasswordField'

type Consent = { type: string; policyVersion: string; grantedAt: string; withdrawnAt: string | null }
type PrivacyExport = { email: string; consents: Consent[] }
type Props = { token: string | null; onTokenChange: (token: string | null) => void; nickname: string | null; onNavigate: (path: string) => void }
type Mode = 'login' | 'register'

/** 토큰은 메모리에만 두고, 로그인과 가입 모두 서버 API로 처리한다. */
export default function AccountAccess({ token, onTokenChange, nickname, onNavigate }: Props) {
  const [privacy, setPrivacy] = useState<PrivacyExport | null>(null)
  const [mode, setMode] = useState<Mode>('login')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [email, setEmail] = useState('')
  const [challengeId, setChallengeId] = useState('')
  const [verificationCode, setVerificationCode] = useState('')
  const [verificationToken, setVerificationToken] = useState('')

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
          ...(register ? { verificationToken } : {}),
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
          ? '가입하지 못했습니다. 이메일 인증, 대문자·소문자·숫자·특수문자를 포함한 10~20자 비밀번호와 필수 동의를 확인해 주세요.'
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

  async function paperLeaderboard(accepted: boolean) {
    if (!token) return
    setBusy(true); setMessage('')
    try {
      const response = await fetch('/api/privacy/paper-leaderboard-consent', { method: 'PATCH', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ accepted }) })
      if (!response.ok) throw new Error('leaderboard')
      const refresh = await fetch('/api/privacy/me', { headers: { Authorization: `Bearer ${token}` } })
      if (!refresh.ok) throw new Error('privacy')
      setPrivacy(await refresh.json() as PrivacyExport); setMessage(accepted ? '월간 랭킹 공개에 동의했습니다.' : '월간 랭킹 공개 동의를 철회하고 기존 기록을 삭제했습니다.')
    } catch { setMessage('월간 랭킹 공개 동의 상태를 변경하지 못했습니다.') } finally { setBusy(false) }
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
  const paperLeaderboardConsent = privacy?.consents.find((consent) => consent.type === 'PAPER_LEADERBOARD' && !consent.withdrawnAt)
  return <section className="panel-card" aria-labelledby="account-heading">
    <p className="eyebrow">계정</p><h2 className="panel-title" id="account-heading">{token ? '마이 페이지' : mode === 'login' ? '다시 오신 것을 환영해요' : '계정을 만들어 보세요'}</h2>
    <p className="panel-description">비밀번호는 안전하게 암호화해 저장하고, 로그인 토큰은 브라우저 메모리에만 보관합니다.</p>
    {!token ? <>
      <div className="mt-6 flex gap-2 border-b border-slate-200" role="tablist" aria-label="계정 작업">
        <button className={`px-4 py-3 text-sm font-semibold ${mode === 'login' ? 'border-b-2 border-blue-600 text-blue-700' : 'text-slate-500'}`} aria-selected={mode === 'login'} onClick={() => { setMode('login'); setMessage('') }} role="tab" type="button">로그인</button>
        <button className={`px-4 py-3 text-sm font-semibold ${mode === 'register' ? 'border-b-2 border-blue-600 text-blue-700' : 'text-slate-500'}`} aria-selected={mode === 'register'} onClick={() => { setMode('register'); setMessage('') }} role="tab" type="button">회원가입</button>
      </div>
      <form className="mt-6 grid max-w-xl gap-4" onSubmit={authenticate}>
        <label className="field-label">이메일<input className="field-input" name="email" type="email" autoComplete="email" required maxLength={254} value={email} onChange={(event) => { setEmail(event.target.value); setVerificationToken(''); setChallengeId('') }} /></label>
        <label className="field-label">비밀번호<PasswordField className="field-input" name="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required maxLength={mode === 'login' ? 128 : 20} minLength={mode === 'register' ? 10 : undefined} pattern={mode === 'register' ? '(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9])[!-~]{10,20}' : undefined} /></label>
        {mode === 'register' && <div className="grid gap-2">
          <button className="secondary-button w-fit" type="button" disabled={busy || !email} onClick={async () => { setBusy(true); try { const r = await fetch('/api/auth/email-verification', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email }) }); if (!r.ok) throw new Error(); setChallengeId((await r.json() as { challengeId: string }).challengeId); setMessage('인증 코드를 이메일로 보냈습니다.') } catch { setMessage('인증 메일을 보내지 못했습니다. 이메일 및 메일 서버 설정을 확인해 주세요.') } finally { setBusy(false) } }}>{challengeId ? '인증 코드 다시 받기' : '인증 코드 받기'}</button>
          {challengeId && <div className="flex gap-2"><label className="field-label flex-1">이메일 인증 코드<input className="field-input" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={verificationCode} onChange={(e) => setVerificationCode(e.target.value)} /></label><button className="secondary-button self-end" type="button" disabled={busy || verificationCode.length !== 6} onClick={async () => { setBusy(true); try { const r = await fetch('/api/auth/email-verification/confirm', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ challengeId, email, code: verificationCode }) }); if (!r.ok) throw new Error(); setVerificationToken((await r.json() as { verificationToken: string }).verificationToken); setMessage('이메일 인증을 완료했습니다.') } catch { setMessage('인증 코드가 올바르지 않거나 만료되었습니다.') } finally { setBusy(false) } }}>인증 확인</button></div>}
          {verificationToken && <p className="text-sm text-emerald-700">이메일 인증 완료</p>}
        </div>}
        {mode === 'register' && <>
          <label className="flex items-start gap-3 text-sm leading-6"><input className="mt-1 size-4" name="privacyAccepted" type="checkbox" required /><span><a className="text-blue-700 underline" href="/privacy" target="_blank" rel="noreferrer">개인정보 처리방침</a>을 읽고 개인정보 처리에 동의합니다. (필수)</span></label>
          <label className="flex items-start gap-3 text-sm leading-6"><input className="mt-1 size-4" name="marketingAccepted" type="checkbox" /><span>서비스 소식과 마케팅 정보 수신에 동의합니다. (선택)</span></label>
        </>}
        <button className="primary-button mt-2 w-full sm:w-fit" disabled={busy || (mode === 'register' && !verificationToken)} type="submit">{busy ? '처리 중...' : mode === 'login' ? '로그인' : '회원가입'}</button>
      </form>
    </> : <div className="mt-6 grid max-w-xl gap-4">
      <p className="rounded-lg bg-slate-50 p-3 text-sm text-slate-700">현재 닉네임: <strong>{nickname}</strong></p>
      <p className="rounded-xl bg-slate-50 p-4 text-sm" aria-live="polite">로그인 계정: {privacy?.email ?? '정보를 불러오는 중입니다.'}</p>
      <button className="secondary-button w-fit" type="button" onClick={() => onNavigate('/account/edit')}>내 정보 수정</button>
      <div className="rounded-xl bg-slate-50 p-4" aria-label="월간 모의거래 랭킹 공개 설정">
        <p className="text-sm font-semibold text-slate-800">월간 모의거래 랭킹 공개</p>
        <p className="mt-1 text-sm text-slate-600">동의하면 닉네임과 월간 수익률을 공개합니다. 이메일과 가상 잔액은 공개하지 않습니다. 철회하면 참가 기록과 메달을 삭제합니다.</p>
        <button className="secondary-button mt-3" disabled={busy} aria-pressed={!!paperLeaderboardConsent} onClick={() => paperLeaderboard(!paperLeaderboardConsent)} type="button">{paperLeaderboardConsent ? '랭킹 공개 동의 철회' : '랭킹 공개 선택 동의'}</button>
      </div>      <div className="flex flex-col gap-3 sm:flex-row"><button className="secondary-button" disabled={busy} onClick={() => marketing(!marketingConsent)} type="button">{marketingConsent ? '마케팅 수신 철회' : '마케팅 수신 동의'}</button><button className="danger-button" disabled={busy} onClick={deleteAccount} type="button">계정 삭제</button></div>
      <p className="text-sm text-slate-500">계정 삭제 시 거래소 API key를 삭제하고 직접 식별정보를 익명화합니다.</p>
    </div>}
    {message && <p className="mt-4 text-sm text-blue-800" role="status">{message}</p>}
  </section>
}