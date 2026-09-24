import { useState } from 'react'
import type { FormEvent } from 'react'

type Props = { token: string; current: string | null; required: boolean; onSaved: (nickname: string) => void }
const validNickname = /^[A-Za-z0-9가-힣]{2,8}$/

/** Availability is checked on the server and must match the value submitted. */
export default function NicknameEditor({ token, current, required, onSaved }: Props) {
  const [nickname, setNickname] = useState(current ?? '')
  const [checked, setChecked] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const valid = validNickname.test(nickname)
  const unchanged = nickname === current

  async function checkAvailability() {
    setMessage('')
    setChecked('')
    if (!valid) { setMessage('한글, 영문, 숫자만 사용해 2~8자로 입력해 주세요.'); return }
    setBusy(true)
    try {
      const response = await fetch(`/api/account/nickname/availability?nickname=${encodeURIComponent(nickname)}`, { headers: { Authorization: `Bearer ${token}` } })
      if (!response.ok) throw new Error('availability')
      const result = await response.json() as { valid: boolean; available: boolean }
      if (!result.valid) setMessage('한글, 영문, 숫자만 사용해 2~8자로 입력해 주세요.')
      else if (!result.available) setMessage('이미 사용 중인 닉네임입니다. 다른 이름을 입력해 주세요.')
      else { setChecked(nickname); setMessage('사용할 수 있는 닉네임입니다.') }
    } catch { setMessage('중복확인을 하지 못했습니다. 연결 상태를 확인하고 다시 시도해 주세요.') }
    finally { setBusy(false) }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!valid || checked !== nickname) { setMessage('저장 전에 닉네임 중복확인을 해 주세요.'); return }
    setBusy(true)
    try {
      const response = await fetch('/api/account/nickname', { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ nickname }) })
      if (response.status === 409) { setChecked(''); setMessage('이미 사용 중인 닉네임입니다. 다시 중복확인해 주세요.'); return }
      if (!response.ok) throw new Error('nickname')
      onSaved(nickname)
      setMessage('닉네임을 저장했습니다.')
    } catch { setMessage('닉네임을 저장하지 못했습니다. 다시 시도해 주세요.') }
    finally { setBusy(false) }
  }

  return <section className={required ? 'panel-card mx-auto w-full max-w-lg' : 'rounded-xl border border-slate-200 bg-white p-5'} role={required ? 'dialog' : undefined} aria-modal={required || undefined} aria-labelledby="nickname-heading">
    <p className="eyebrow">계정 설정</p>
    <h2 className="panel-title" id="nickname-heading">{required ? '사용할 닉네임을 정해 주세요' : '닉네임 변경'}</h2>
    <p className="panel-description">닉네임은 2~8자의 한글, 영문, 숫자로 입력해 주세요. 공백과 특수문자, 자음·모음 단독 표기는 사용할 수 없습니다.</p>
    <form className="mt-5 grid gap-3 sm:grid-cols-[1fr_auto_auto]" onSubmit={submit}>
      <label className="sr-only" htmlFor="account-nickname">닉네임</label>
      <input id="account-nickname" className="field-input" autoFocus={required} autoComplete="nickname" maxLength={8} minLength={2} value={nickname} onChange={(event) => { setNickname(event.target.value); setChecked(''); setMessage('') }} required aria-describedby="nickname-rules" />
      <button className="secondary-button" type="button" disabled={busy || unchanged} onClick={checkAvailability}>중복확인</button>
      <button className="primary-button" type="submit" disabled={busy || unchanged || !valid || checked !== nickname}>{busy ? '확인 중…' : '저장'}</button>
    </form>
    <p id="nickname-rules" className="mt-2 text-xs text-slate-500">한글 완성형·영문·숫자만, 2~8자</p>
    {message && <p className="mt-3 text-sm text-blue-800" role="status">{message}</p>}
  </section>
}