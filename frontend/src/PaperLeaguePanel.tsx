import { useCallback, useEffect, useState } from 'react'

type Standing = { nickname: string; returnPercent: number; tradeCount: number; place: number | null; badge: string | null }
type Board = { month: string; status: string; marketDataAt: string | null; standings: Standing[]; latestCompletedMonth: string | null; latestAwards: Standing[] }
type Entry = { publicConsent: boolean; nextMonth: string; enrolled: boolean }
type Props = { token: string | null; onNavigate: (path: string) => void }

function monthLabel(month: string) { const [year, number] = month.split('-'); return `${year}년 ${Number(number)}월` }
function badgeLabel(badge: string | null) { return badge === 'GOLD' ? 'Gold' : badge === 'SILVER' ? 'Silver' : badge === 'BRONZE' ? 'Bronze' : '' }
function percent(value: number) { return `${value > 0 ? '+' : ''}${value.toFixed(2)}%` }

export default function PaperLeaguePanel({ token, onNavigate }: Props) {
  const [board, setBoard] = useState<Board | null>(null)
  const [entry, setEntry] = useState<Entry | null>(null)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async (isActive: () => boolean) => {
    const response = await fetch('/api/paper-league')
    if (!response.ok) throw new Error('board')
    const nextBoard = await response.json() as Board
    if (isActive()) setBoard(nextBoard)
    if (token) {
      const entryResponse = await fetch('/api/paper-league/entry', { headers: { Authorization: `Bearer ${token}` } })
      if (!entryResponse.ok) throw new Error('entry')
      const nextEntry = await entryResponse.json() as Entry
      if (isActive()) setEntry(nextEntry)
    } else if (isActive()) setEntry(null)
  }, [token])
  useEffect(() => { let active = true; load(() => active).catch(() => { if (active) setMessage('월간 랭킹을 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.') }); return () => { active = false } }, [load])
  async function enroll() {
    if (!token) return onNavigate('/account')
    setBusy(true); setMessage('')
    try {
      const response = await fetch('/api/paper-league/entry', { method: 'POST', headers: { Authorization: `Bearer ${token}` } })
      if (!response.ok) throw new Error('enroll')
      setEntry(await response.json() as Entry); setMessage('다음 달 모의거래 대회 참가를 신청했습니다.')
    } catch { setMessage('대회 신청에 실패했습니다. 공개 동의 상태를 확인해 주세요.') }
    finally { setBusy(false) }
  }

  return <section className="panel-card space-y-4" aria-labelledby="paper-league-heading">
    <div><p className="eyebrow">월간 모의거래 대회</p><h2 className="panel-title" id="paper-league-heading">{board ? monthLabel(board.month) : '랭킹'}</h2><p className="panel-description">매월 시작·종료 평가액으로 수익률을 계산합니다. 거래소별 모의 지갑 전체를 반영하고, 체결이 있는 참가자만 순위에 포함합니다.</p></div>
    {token && (!entry?.publicConsent ? <div className="rounded-lg bg-slate-50 p-3 text-sm text-slate-700"><p>공개 랭킹은 선택 동의 후 참가할 수 있습니다.</p><button className="secondary-button mt-2" type="button" onClick={() => onNavigate('/account')}>계정에서 공개 동의 설정</button></div> : entry.enrolled ? <p className="rounded-lg bg-blue-50 p-3 text-sm text-blue-900" role="status">{monthLabel(entry.nextMonth)} 대회 참가 신청 완료</p> : <button className="primary-button" disabled={busy} onClick={enroll} type="button">{monthLabel(entry.nextMonth)} 대회 참가 신청</button>)}
    {board?.marketDataAt && <p className="text-xs text-slate-500">Valuation snapshot: {new Date(board.marketDataAt).toLocaleString('ko-KR')}</p>}
    {board?.standings.length ? <ol className="divide-y divide-slate-100" aria-label={`${monthLabel(board.month)} 모의거래 순위`}>{board.standings.map((row) => <li className="flex items-center justify-between gap-3 py-3" key={row.nickname}><span className="font-semibold">{row.place}위 · {row.nickname} {badgeLabel(row.badge) && <span aria-label={`${badgeLabel(row.badge)} 메달`}>{badgeLabel(row.badge)}</span>}</span><span className={row.returnPercent >= 0 ? 'text-emerald-700' : 'text-rose-700'}>{percent(row.returnPercent)} · 체결 {row.tradeCount}회</span></li>)}</ol> : <p className="rounded-lg bg-slate-50 p-3 text-sm text-slate-600">{board?.status === 'WAITING' ? '대회 시작 또는 첫 체결을 기다리는 중입니다.' : '아직 순위가 없습니다.'}</p>}
    {!!board?.latestAwards.length && <div className="border-t border-slate-200 pt-4"><h3 className="section-title">{monthLabel(board.latestCompletedMonth!)} 메달</h3><ul className="mt-2 flex flex-wrap gap-2">{board.latestAwards.map((row) => <li className="rounded-full bg-amber-50 px-3 py-1.5 text-sm font-semibold text-amber-900" key={row.nickname}>{badgeLabel(row.badge)} · {row.nickname}</li>)}</ul></div>}
    <p className="text-xs leading-5 text-slate-500">공개되는 정보는 동의한 참가자의 닉네임, 월간 수익률, 순위와 메달입니다. 수익률은 과거 모의 성과이며 실제 투자 결과를 보장하지 않습니다. 같은 수익률이면 닉네임을 대소문자 구분 없이 오름차순 정렬합니다.</p>
    {message && <p className="text-sm text-blue-800" role="status">{message}</p>}
  </section>
}