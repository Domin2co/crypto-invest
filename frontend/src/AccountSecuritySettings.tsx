import { useEffect, useState } from 'react'
import PasswordField from './PasswordField'

type EmailStatus = { email: string; verified: boolean; changeAvailableAt: string }

export default function AccountSecuritySettings({ token, onEmailChanged }: { token: string; onEmailChanged?: (email: string) => void }) {
  const [status, setStatus] = useState<EmailStatus | null>(null)
  const [email, setEmail] = useState('')
  const [challengeId, setChallengeId] = useState('')
  const [code, setCode] = useState('')
  const [verificationToken, setVerificationToken] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [mfaEnabled, setMfaEnabled] = useState(false)
  const [mfaSecret, setMfaSecret] = useState('')
  const [mfaCode, setMfaCode] = useState('')
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([])
  const [mfaPassword, setMfaPassword] = useState('')
  useEffect(() => { fetch('/api/account/email-status', { headers: { Authorization: `Bearer ${token}` } }).then((response) => response.ok ? response.json() as Promise<EmailStatus> : Promise.reject()).then(setStatus).catch(() => setMessage('이메일 상태를 불러오지 못했습니다.')) }, [token])
  useEffect(() => { fetch('/api/account/mfa', { headers: { Authorization: `Bearer ${token}` } }).then((response) => response.ok ? response.json() as Promise<{ enabled: boolean }> : Promise.reject()).then((result) => setMfaEnabled(result.enabled)).catch(() => setMessage('다중 인증 상태를 불러오지 못했습니다.')) }, [token])
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
  const cooling = !!status && Date.now() < Date.parse(status.changeAvailableAt)
  async function request(path: string, body: object, method = 'POST') { const response = await fetch(path, { method, headers, body: JSON.stringify(body) }); if (!response.ok) throw new Error(String(response.status)); return response }
  return <section className="grid gap-4 rounded-xl border border-slate-200 p-4" aria-labelledby="security-heading">
    <h3 className="font-semibold" id="security-heading">로그인 보안</h3>
    <div className="grid gap-3 rounded-xl bg-slate-50 p-4">
      <h4 className="text-sm font-semibold">인증 앱 다중 인증</h4>
      <p className="text-sm text-slate-600">현재 상태: {mfaEnabled ? '사용 중' : '사용 안 함'}. 로그인할 때 인증 앱 코드가 필요합니다.</p>
      {!mfaEnabled && <form className="grid gap-3" onSubmit={async (event) => { event.preventDefault(); setBusy(true); try { const result = await request('/api/account/mfa/setup', { currentPassword: mfaPassword }); setMfaSecret((await result.json() as { secret: string }).secret); setMessage('인증 앱에서 비밀 키를 등록한 뒤 현재 코드를 입력해 주세요.') } catch { setMessage('다중 인증 설정을 시작하지 못했습니다. 비밀번호를 확인해 주세요.') } finally { setBusy(false) } }}>
        <label className="field-label">현재 비밀번호<PasswordField className="field-input" name="mfaPassword" autoComplete="current-password" required maxLength={128} value={mfaPassword} onChange={(event) => setMfaPassword(event.target.value)} /></label>
        <button className="secondary-button w-fit" disabled={busy}>설정 시작</button>
      </form>}
      {mfaSecret && !mfaEnabled && <div className="grid gap-3"><p className="break-all text-sm">인증 앱에 수동으로 등록할 비밀 키: <strong>{mfaSecret}</strong></p><label className="field-label">인증 앱 코드<input className="field-input" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={mfaCode} onChange={(event) => setMfaCode(event.target.value)} /></label><button className="primary-button w-fit" type="button" disabled={busy || mfaCode.length !== 6} onClick={async () => { setBusy(true); try { const result = await request('/api/account/mfa/confirm', { code: mfaCode }); setRecoveryCodes((await result.json() as { recoveryCodes: string[] }).recoveryCodes); setMfaEnabled(true); setMfaSecret(''); setMfaCode(''); setMessage('다중 인증을 켰습니다. 복구 코드는 지금 안전한 곳에 저장하세요.') } catch { setMessage('코드가 올바르지 않거나 설정이 만료되었습니다.') } finally { setBusy(false) } }}>다중 인증 켜기</button></div>}
      {recoveryCodes.length > 0 && <div className="rounded-lg border border-amber-300 bg-white p-3"><p className="text-sm font-semibold">1회용 복구 코드 · 다시 표시되지 않습니다</p><ul className="mt-2 grid grid-cols-1 gap-1 font-mono text-sm sm:grid-cols-2">{recoveryCodes.map((code) => <li key={code}>{code}</li>)}</ul><button className="secondary-button mt-3" type="button" onClick={() => setRecoveryCodes([])}>저장 완료</button></div>}
      {mfaEnabled && <form className="grid gap-3" onSubmit={async (event) => { event.preventDefault(); const form = new FormData(event.currentTarget); setBusy(true); try { await request('/api/account/mfa', { currentPassword: form.get('currentPassword'), code: mfaCode }, 'DELETE'); setMfaEnabled(false); setMfaCode(''); setRecoveryCodes([]); setMessage('다중 인증을 해제했습니다.') } catch { setMessage('비밀번호와 인증 앱 코드 또는 복구 코드를 확인해 주세요.') } finally { setBusy(false) } }}><label className="field-label">해제 확인 비밀번호<PasswordField className="field-input" name="currentPassword" autoComplete="current-password" required maxLength={128} /></label><label className="field-label">인증 앱 코드 또는 복구 코드<input className="field-input" maxLength={32} value={mfaCode} onChange={(event) => setMfaCode(event.target.value)} required /></label><button className="danger-button w-fit" disabled={busy}>다중 인증 해제</button></form>}
    </div>
    <form className="grid gap-3" onSubmit={async (event) => { event.preventDefault(); const formElement = event.currentTarget; const form = new FormData(formElement); setBusy(true); try { await request('/api/auth/password', { currentPassword: form.get('currentPassword'), newPassword: form.get('newPassword') }, 'PATCH'); formElement.reset(); setMessage('비밀번호를 변경했습니다.') } catch { setMessage('현재 비밀번호 또는 새 비밀번호 형식을 확인해 주세요.') } finally { setBusy(false) } }}>
      <h4 className="text-sm font-semibold">비밀번호 변경</h4>
      <label className="field-label">현재 비밀번호<PasswordField className="field-input" name="currentPassword" autoComplete="current-password" required maxLength={128} /></label>
      <label className="field-label">새 비밀번호<PasswordField className="field-input" name="newPassword" autoComplete="new-password" required minLength={10} maxLength={20} pattern="(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9])[!-~]{10,20}" title="대문자, 소문자, 숫자, 특수문자를 포함한 10~20자" /></label>
      <button className="secondary-button w-fit" disabled={busy}>비밀번호 저장</button>
    </form>
    <div className="grid gap-3 border-t border-slate-200 pt-4">
      <h4 className="text-sm font-semibold">이메일 변경</h4>
      <p className="text-sm text-slate-600">현재 이메일: {status?.email ?? '확인 중'} · {status?.verified ? '인증됨' : '미인증'}</p>
      {cooling ? <p className="text-sm text-slate-600">가입 또는 최근 변경 후 90일이 지나야 변경할 수 있습니다. 가능일: {new Date(status!.changeAvailableAt).toLocaleDateString('ko-KR')}</p> : <>
        <label className="field-label">새 이메일<input className="field-input" type="email" maxLength={254} required value={email} onChange={(event) => { setEmail(event.target.value); setVerificationToken('') }} /></label>
        <button className="secondary-button w-fit" type="button" disabled={busy || !email} onClick={async () => { setBusy(true); try { const result = await request('/api/account/email-verification', { email }); setChallengeId((await result.json() as { challengeId: string }).challengeId); setMessage('새 이메일로 인증 코드를 보냈습니다.') } catch { setMessage('인증 메일을 보내지 못했습니다. 이메일 중복이나 변경 제한을 확인해 주세요.') } finally { setBusy(false) } }}>인증 코드 받기</button>
        {challengeId && <div className="flex gap-2"><label className="field-label flex-1">인증 코드<input className="field-input" inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={code} onChange={(event) => setCode(event.target.value)} /></label><button className="secondary-button self-end" type="button" disabled={busy || code.length !== 6} onClick={async () => { setBusy(true); try { const result = await request('/api/account/email-verification/confirm', { challengeId, email, code }); setVerificationToken((await result.json() as { verificationToken: string }).verificationToken); setMessage('새 이메일 인증을 완료했습니다.') } catch { setMessage('인증 코드가 올바르지 않거나 만료되었습니다.') } finally { setBusy(false) } }}>인증 확인</button></div>}
        {verificationToken && <button className="primary-button w-fit" type="button" disabled={busy} onClick={async () => { setBusy(true); try { await request('/api/account/email', { email, verificationToken }, 'PATCH'); const result = await fetch('/api/account/email-status', { headers: { Authorization: `Bearer ${token}` } }); if (result.ok) setStatus(await result.json() as EmailStatus); onEmailChanged?.(email); setEmail(''); setChallengeId(''); setVerificationToken(''); setMessage('이메일을 변경했습니다. 다음 변경은 90일 후 가능합니다.') } catch { setMessage('이메일을 변경하지 못했습니다. 다시 인증해 주세요.') } finally { setBusy(false) } }}>이메일 변경 저장</button>}
      </>}
    </div>
    {message && <p className="text-sm text-blue-800" role="status">{message}</p>}
  </section>
}