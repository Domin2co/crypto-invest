import { useEffect, useState } from 'react'

type User = { id: string; email: string; nickname: string | null; role: 'USER' | 'ADMIN'; createdAt: string }
type RoleChange = { actorUserId: string; targetUserId: string; actorLabel: string; targetLabel: string; previousRole: string; newRole: string; reason: string; changedAt: string }
type Results = { users: User[]; recentChanges: RoleChange[] }
export default function AdminUserManagement({ token }: { token: string }) {
  const [query, setQuery] = useState(''); const [data, setData] = useState<Results>({ users: [], recentChanges: [] }); const [reason, setReason] = useState(''); const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const [revision, setRevision] = useState(0)
  useEffect(() => {
    if (query.trim().length === 1) { setData(value => ({ ...value, users: [] })); return }
    const controller = new AbortController()
    fetch('/api/admin/users?q=' + encodeURIComponent(query.trim()), { headers: { Authorization: 'Bearer ' + token }, signal: controller.signal }).then(async response => { if (!response.ok) throw Error(); setData(await response.json()) }).catch(e => { if (e.name !== 'AbortError') setError('사용자 정보를 불러오지 못했습니다.') })
    return () => controller.abort()
  }, [token, query, revision])
  async function changeRole(user: User) {
    if (reason.trim().length < 5) { setError('변경 사유를 5자 이상 입력해 주세요.'); return }
    setBusy(true); setError('')
    try {
      const response = await fetch('/api/admin/users/' + user.id + '/role', { method: 'PATCH', headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, body: JSON.stringify({ role: user.role === 'ADMIN' ? 'USER' : 'ADMIN', reason }) })
      if (!response.ok) throw Error()
      setReason(''); setRevision(value => value + 1)
    } catch { setError('권한을 변경하지 못했습니다. 마지막 관리자 보호 또는 최신 권한 상태를 확인해 주세요.') } finally { setBusy(false) }
  }
  return <section className="space-y-5" aria-labelledby="admin-users-title"><div><h2 className="page-heading" id="admin-users-title">사용자 권한 관리</h2><p className="panel-description">이메일 또는 닉네임으로 검색하고, 사유를 기록한 뒤 관리자 권한을 부여하거나 회수합니다.</p></div>
    <div className="panel-card space-y-3"><label className="block text-sm font-semibold" htmlFor="admin-user-query">사용자 검색</label><input className="rounded-lg border border-slate-300 bg-slate-50 px-3 py-2 text-slate-950 w-full" id="admin-user-query" value={query} maxLength={80} onChange={event => setQuery(event.target.value)} placeholder="이메일 또는 닉네임 (2자 이상)" autoComplete="off" /><label className="block text-sm font-semibold" htmlFor="admin-role-reason">권한 변경 사유 (5~500자)</label><textarea className="rounded-lg border border-slate-300 bg-slate-50 px-3 py-2 text-slate-950 min-h-20 w-full" id="admin-role-reason" value={reason} maxLength={500} onChange={event => setReason(event.target.value)} /><p className="text-xs text-slate-500" role="status">{query.trim().length < 2 ? '검색어를 2자 이상 입력해 주세요.' : `${data.users.length}명 표시 (최대 20명)`}</p></div>
    {error && <p className="text-sm text-red-700" role="alert">{error}</p>}
    {data.users.length > 0 && <ul className="space-y-3">{data.users.map(user => <li key={user.id} className="panel-card flex flex-wrap items-center justify-between gap-3"><div><p className="font-semibold">{user.nickname || '닉네임 미설정'} · {user.role}</p><p className="text-sm text-slate-600">{user.email}</p><p className="text-xs text-slate-500">가입 {new Date(user.createdAt).toLocaleDateString('ko-KR')}</p></div><button className={user.role === 'ADMIN' ? 'secondary-button' : 'primary-button'} disabled={busy || reason.trim().length < 5} onClick={() => void changeRole(user)}>{user.role === 'ADMIN' ? '관리자 권한 회수' : '관리자 권한 부여'}</button></li>)}</ul>}
    <div className="panel-card"><h3 className="section-title">최근 권한 변경 기록</h3>{data.recentChanges.length ? <ul className="mt-3 space-y-2 text-sm">{data.recentChanges.map((change, i) => <li key={change.actorUserId + change.targetUserId + change.changedAt + i} className="border-t border-slate-100 pt-2">{change.actorLabel} → {change.targetLabel} · {change.previousRole} → {change.newRole} · {change.reason} · {new Date(change.changedAt).toLocaleString('ko-KR')}</li>)}</ul> : <p className="mt-2 text-sm text-slate-600">변경 기록이 없습니다. 사용자 검색 시 최근 기록을 표시합니다.</p>}</div>
  </section>
}
