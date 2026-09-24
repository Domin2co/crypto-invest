import { useState } from 'react'
import type { InputHTMLAttributes } from 'react'

export default function PasswordField(props: Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>) {
  const [visible, setVisible] = useState(false)
  return <span className="relative block">
    <input {...props} className={(props.className || '') + ' w-full box-border pr-12'} type={visible ? 'text' : 'password'} />
    <button type="button" className="absolute inset-y-0 right-2 px-2 text-slate-500 hover:text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-600" aria-label={visible ? '비밀번호 숨기기' : '비밀번호 표시'} aria-pressed={visible} onClick={() => setVisible((value) => !value)}>
      <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="size-5"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>{!visible && <path d="m4 4 16 16"/>}</svg>
    </button>
  </span>
}
