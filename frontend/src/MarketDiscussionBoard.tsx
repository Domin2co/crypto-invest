import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'

type PostSummary = { id: string; number: number; title: string; nickname: string; createdAt: string; upVotes: number }
type Comment = { id: string; nickname: string; content: string; createdAt: string; canEdit: boolean }
type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
type PostDetail = { id: string; symbol: string; nickname: string; title: string; content: string | null; fontFamily: string; fontSize: number; textAlign: 'left' | 'center' | 'right'; hasImage: boolean; createdAt: string; updatedAt: string; upVotes: number; downVotes: number; blind: boolean; hidden: boolean; canEdit: boolean; userVote: number | null }
const blankPage = <T,>(): Page<T> => ({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })

export default function MarketDiscussionBoard({ symbol, token }: { symbol: string; token?: string | null }) {
  const [result,setResult]=useState<Page<PostSummary>>(blankPage())
  const [comments,setComments]=useState<Page<Comment>>(blankPage())
  const [title,setTitle]=useState(''); const [body,setBody]=useState('')
  const [fontFamily,setFontFamily]=useState('system'); const [fontSize,setFontSize]=useState(16)
  const [textAlign,setTextAlign]=useState<'left'|'center'|'right'>('left')
  const [image,setImage]=useState<{imageType:string;imageData:string}|null>(null)
  const [page,setPage]=useState(0); const [commentPage,setCommentPage]=useState(0)
  const [revision,setRevision]=useState(0); const [selectedId,setSelectedId]=useState<string|null>(null)
  const [detail,setDetail]=useState<PostDetail|null>(null); const [reveal,setReveal]=useState(false)
  const [editingPost,setEditingPost]=useState(false); const [editingComment,setEditingComment]=useState<string|null>(null)
  const [editCommentText,setEditCommentText]=useState(''); const [comment,setComment]=useState('')
  const [reportReason,setReportReason]=useState('OTHER'); const [reportDetails,setReportDetails]=useState('')
  const [detailRevision,setDetailRevision]=useState(0); const [loading,setLoading]=useState(true)
  const [busy,setBusy]=useState(false); const [message,setMessage]=useState('')
  const authHeaders = (): Record<string,string> => token ? { Authorization:'Bearer '+token } : {}
  const endpoint = '/api/discussions/'+encodeURIComponent(symbol)

  useEffect(()=>{ const controller=new AbortController(); setLoading(true)
    fetch(endpoint+'?page='+page,{signal:controller.signal}).then(async r=>{if(!r.ok)throw Error();const p=await r.json();if(Array.isArray(p)){const rows=(p as Array<Partial<PostSummary>&{content?:string}>).map((x,i)=>({id:x.id??String(i),number:x.number??page*10+i+1,title:x.title??x.content?.slice(0,120)??'Untitled',nickname:x.nickname??'—',createdAt:x.createdAt??new Date(0).toISOString(),upVotes:x.upVotes??0}));setResult({content:rows.slice(page*10,page*10+10),page,size:10,totalElements:rows.length,totalPages:Math.ceil(rows.length/10)})}else setResult(Array.isArray(p?.content)?p:blankPage<PostSummary>())})
      .catch(e=>{if(e.name!=='AbortError')setMessage('토론방 목록을 불러오지 못했습니다.')}).finally(()=>{if(!controller.signal.aborted)setLoading(false)})
    return ()=>controller.abort()
  },[endpoint,page,revision])
  useEffect(()=>{setSelectedId(null);setDetail(null);setPage(0)},[symbol])
  useEffect(()=>{if(!selectedId)return;const controller=new AbortController()
    fetch(endpoint+'/'+selectedId+(reveal?'?reveal=true':''),{headers:token?{Authorization:'Bearer '+token}:{},signal:controller.signal}).then(async r=>{if(!r.ok)throw Error();setDetail(await r.json())})
      .catch(e=>{if(e.name!=='AbortError')setMessage('글 내용을 불러오지 못했습니다.')})
    return ()=>controller.abort()
  },[endpoint,selectedId,token,reveal,detailRevision])
  useEffect(()=>{if(!selectedId||!detail||detail.hidden)return;const controller=new AbortController()
    fetch(endpoint+'/'+selectedId+'/comments?page='+commentPage,{headers:token?{Authorization:'Bearer '+token}:{},signal:controller.signal}).then(async r=>{if(!r.ok)throw Error();setComments(await r.json())})
      .catch(e=>{if(e.name!=='AbortError')setMessage('댓글을 불러오지 못했습니다.')})
    return ()=>controller.abort()
  },[endpoint,selectedId,token,commentPage,detail,detailRevision])

  async function selectImage(file?:File){setImage(null);if(!file)return
    if(!['image/png','image/jpeg','image/webp'].includes(file.type)||file.size>512*1024){setMessage('PNG, JPEG, WebP 형식의 512KB 이하 이미지만 첨부할 수 있습니다.');return}
    const data=await new Promise<string>((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(String(reader.result).split(',')[1]??'');reader.onerror=reject;reader.readAsDataURL(file)})
    setImage({imageType:file.type,imageData:data})
  }
  function loadPostForm(post?:PostDetail){setTitle(post?.title??'');setBody(post?.content??'');setFontFamily(post?.fontFamily??'system');setFontSize(post?.fontSize??16);setTextAlign(post?.textAlign??'left');setImage(null);setEditingPost(!!post)}
  async function submitPost(e:FormEvent<HTMLFormElement>){e.preventDefault();if(!token)return;setBusy(true);setMessage('')
    try{const r=await fetch(editingPost&&selectedId?endpoint+'/'+selectedId:endpoint,{method:editingPost?'PATCH':'POST',headers:{...authHeaders(),'Content-Type':'application/json'},body:JSON.stringify({title,content:body,fontFamily,fontSize,textAlign,...image})});if(!r.ok)throw Error('게시글을 저장하지 못했습니다.')
      setEditingPost(false);setImage(null);setDetailRevision(x=>x+1);setRevision(x=>x+1);setMessage(editingPost?'게시글을 수정했습니다.':'게시글을 등록했습니다.')
    }catch(e){setMessage(e instanceof Error?e.message:'게시글을 저장하지 못했습니다.')}finally{setBusy(false)}
  }
  async function removePost(){if(!token||!selectedId||!window.confirm('게시글을 삭제할까요? 댓글과 평가도 함께 삭제됩니다.'))return
    setBusy(true);try{const r=await fetch(endpoint+'/'+selectedId,{method:'DELETE',headers:authHeaders()});if(!r.ok)throw Error();setSelectedId(null);setDetail(null);setRevision(x=>x+1);setMessage('게시글을 삭제했습니다.')}
    catch{setMessage('게시글을 삭제하지 못했습니다.')}finally{setBusy(false)}
  }
  async function vote(direction:'UP'|'DOWN'){if(!token||!selectedId)return;setBusy(true)
    try{const r=await fetch(endpoint+'/'+selectedId+'/vote',{method:'POST',headers:{...authHeaders(),'Content-Type':'application/json'},body:JSON.stringify({direction})});if(!r.ok)throw Error();setDetailRevision(x=>x+1);setRevision(x=>x+1);setMessage(direction==='UP'?'추천을 반영했습니다.':'비추천을 반영했습니다.')}
    catch{setMessage('추천 상태를 저장하지 못했습니다.')}finally{setBusy(false)}
  }
  async function submitComment(e:FormEvent<HTMLFormElement>){e.preventDefault();if(!token||!selectedId||!comment.trim())return;setBusy(true)
    try{const r=await fetch(endpoint+'/'+selectedId+'/comments',{method:'POST',headers:{...authHeaders(),'Content-Type':'application/json'},body:JSON.stringify({content:comment})});if(!r.ok)throw Error();setComment('');setCommentPage(0);setDetailRevision(x=>x+1);setMessage('댓글을 등록했습니다.')}
    catch{setMessage('댓글을 등록하지 못했습니다.')}finally{setBusy(false)}
  }
  async function saveComment(id:string){if(!token||!editCommentText.trim())return;setBusy(true)
    try{const r=await fetch(endpoint+'/'+selectedId+'/comments/'+id,{method:'PATCH',headers:{...authHeaders(),'Content-Type':'application/json'},body:JSON.stringify({content:editCommentText})});if(!r.ok)throw Error();setEditingComment(null);setDetailRevision(x=>x+1);setMessage('댓글을 수정했습니다.')}
    catch{setMessage('댓글을 수정하지 못했습니다.')}finally{setBusy(false)}
  }
  async function removeComment(id:string){if(!token||!window.confirm('댓글을 삭제할까요?'))return;setBusy(true)
    try{const r=await fetch(endpoint+'/'+selectedId+'/comments/'+id,{method:'DELETE',headers:authHeaders()});if(!r.ok)throw Error();setDetailRevision(x=>x+1);setMessage('댓글을 삭제했습니다.')}
    catch{setMessage('댓글을 삭제하지 못했습니다.')}finally{setBusy(false)}
  }
  async function reportPost(e:FormEvent<HTMLFormElement>){e.preventDefault();if(!token||!selectedId)return;setBusy(true)
    try{const r=await fetch(endpoint+'/'+selectedId+'/reports',{method:'POST',headers:{...authHeaders(),'Content-Type':'application/json'},body:JSON.stringify({reason:reportReason,details:reportDetails})});if(!r.ok)throw Error(r.status===409?'이미 신고한 게시글입니다.':'신고를 접수하지 못했습니다.');setMessage('신고를 접수했습니다.');setReportDetails('')}
    catch(e){setMessage(e instanceof Error?e.message:'신고를 접수하지 못했습니다.')}finally{setBusy(false)}
  }
  const postStyle=detail?{fontFamily:detail.fontFamily==='serif'?'serif':detail.fontFamily==='mono'?'monospace':'inherit',fontSize:detail.fontSize,textAlign:detail.textAlign} as const:undefined
  return <section className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm" aria-labelledby="discussion-heading">
    <div className="flex flex-wrap items-baseline justify-between gap-2"><h3 className="section-title" id="discussion-heading">{symbol} 종목 토론방</h3><span className="text-xs text-slate-500">전체 {result.totalElements}개 · 페이지당 10개</span></div>
    <p className="mt-2 text-xs leading-5 text-slate-500">비밀번호, API 키 등 개인정보나 민감정보를 게시하지 마세요. 게시글은 종목별로 공개됩니다.</p>
    {!selectedId&&token&&<form className="mt-3 grid gap-2" aria-label="종목 토론방 글 작성" onSubmit={submitPost}>
      <label className="field-label" htmlFor="discussion-title">제목<input id="discussion-title" className="field-input" value={title} onChange={e=>setTitle(e.target.value)} maxLength={120} required/></label>
      <label className="sr-only" htmlFor="discussion-content">토론 내용</label><textarea id="discussion-content" className="field-input min-h-24 resize-y" value={body} onChange={e=>setBody(e.target.value)} maxLength={1000} required placeholder="종목에 관한 의견을 나눠 주세요. (최대 1,000자)"/>
      <div className="flex flex-wrap items-center gap-2"><label className="text-xs">글꼴<select className="field-input ml-1 py-1" value={fontFamily} onChange={e=>setFontFamily(e.target.value)}><option value="system">기본</option><option value="serif">명조</option><option value="mono">고정폭</option></select></label><label className="text-xs">크기<select className="field-input ml-1 py-1" value={fontSize} onChange={e=>setFontSize(Number(e.target.value))}>{[14,16,18,20,22,24].map(size=><option key={size} value={size}>{size}px</option>)}</select></label><label className="text-xs">정렬<select className="field-input ml-1 py-1" value={textAlign} onChange={e=>setTextAlign(e.target.value as typeof textAlign)}><option value="left">왼쪽</option><option value="center">가운데</option><option value="right">오른쪽</option></select></label><label className="text-xs">이미지<input className="ml-1 max-w-44" type="file" accept="image/png,image/jpeg,image/webp" onChange={e=>void selectImage(e.target.files?.[0])}/></label>{image&&<span className="text-xs text-slate-600">이미지 첨부됨</span>}<span className="ml-auto text-xs text-slate-500">{body.length}/1,000</span><button className="primary-button" type="submit" disabled={busy||!title.trim()||!body.trim()}>게시글 등록</button></div>
    </form>}
    {!token&&!selectedId&&<p className="mt-3 text-sm text-slate-600"><a className="font-semibold text-blue-700 underline" href="/account">로그인</a> 후 글과 댓글을 작성할 수 있습니다.</p>}
    {message&&<p className="mt-2 text-sm text-slate-700" role="status">{message}</p>}
    {selectedId?<div className="mt-4 border-t border-slate-200 pt-4">{!detail?<p className="py-4 text-sm text-slate-500" role="status">글 내용을 불러오는 중입니다.</p>:<>
      <button className="secondary-button mb-3" type="button" onClick={()=>{setSelectedId(null);setDetail(null);setReveal(false);setEditingPost(false)}}>토론방 목록</button>
      {detail.hidden?<article className="rounded-lg bg-amber-50 p-4" role="status"><h4 className="font-semibold">관리자 검토로 숨김 처리된 글입니다.</h4></article>:editingPost?<form className="grid gap-2" aria-label="게시글 수정" onSubmit={submitPost}><label className="field-label">제목<input className="field-input" value={title} maxLength={120} onChange={e=>setTitle(e.target.value)} required/></label><label className="field-label">내용<textarea className="field-input min-h-28" value={body} maxLength={1000} onChange={e=>setBody(e.target.value)} required/></label><div className="flex flex-wrap gap-2"><select aria-label="글꼴" className="field-input" value={fontFamily} onChange={e=>setFontFamily(e.target.value)}><option value="system">기본 글꼴</option><option value="serif">명조</option><option value="mono">고정폭</option></select><select aria-label="글자 크기" className="field-input" value={fontSize} onChange={e=>setFontSize(Number(e.target.value))}>{[14,16,18,20,22,24].map(n=><option key={n} value={n}>{n}px</option>)}</select><select aria-label="정렬" className="field-input" value={textAlign} onChange={e=>setTextAlign(e.target.value as typeof textAlign)}><option value="left">왼쪽 정렬</option><option value="center">가운데 정렬</option><option value="right">오른쪽 정렬</option></select><label className="text-sm">이미지<input type="file" accept="image/png,image/jpeg,image/webp" onChange={e=>void selectImage(e.target.files?.[0])}/></label><button className="primary-button" disabled={busy||!title.trim()||!body.trim()}>저장</button><button type="button" className="secondary-button" onClick={()=>{setEditingPost(false);setImage(null)}}>취소</button></div></form>:<>
      <article aria-labelledby="discussion-post-title"><h4 id="discussion-post-title" className="text-xl font-bold text-slate-950">{detail.title}</h4><p className="mt-2 text-xs text-slate-500">{detail.nickname} · <time dateTime={detail.createdAt}>{new Date(detail.createdAt).toLocaleString('ko-KR')}</time>{detail.updatedAt!==detail.createdAt&&' · 수정됨'}</p>
        {detail.blind&&!reveal?<div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-4"><p className="font-semibold text-amber-900">비추천이 21개 이상 누적되어 글 내용이 블라인드 처리되었습니다.</p><button type="button" className="secondary-button mt-3" onClick={()=>setReveal(true)}>그래도 내용 보기</button></div>:<div className="mt-4 whitespace-pre-wrap break-words leading-7 text-slate-800" style={postStyle}>{detail.content}</div>}
        {detail.hasImage&&(!detail.blind||reveal)&&<img className="mt-4 max-h-96 rounded-lg object-contain" src={endpoint+'/'+detail.id+'/image'+(reveal?'?reveal=true':'')} alt="게시글 첨부 이미지"/>}
      </article>
      <div className="mt-4 flex flex-wrap gap-2 border-y border-slate-200 py-3" aria-label="게시글 평가"><button type="button" className={detail.userVote===1?'rounded-lg bg-rose-100 px-3 py-2 font-semibold text-rose-800 ring-1 ring-rose-300':'rounded-lg bg-slate-100 px-3 py-2 text-slate-700'} disabled={!token||busy||detail.hidden} aria-pressed={detail.userVote===1} onClick={()=>void vote('UP')}>추천 {detail.upVotes}</button><button type="button" className={detail.userVote===-1?'rounded-lg bg-blue-100 px-3 py-2 font-semibold text-blue-800 ring-1 ring-blue-300':'rounded-lg bg-slate-100 px-3 py-2 text-slate-700'} disabled={!token||busy||detail.hidden} aria-pressed={detail.userVote===-1} onClick={()=>void vote('DOWN')}>비추천 {detail.downVotes}</button>{detail.canEdit&&<><button type="button" className="secondary-button" onClick={()=>loadPostForm(detail)}>수정</button><button type="button" className="secondary-button" disabled={busy} onClick={()=>void removePost()}>삭제</button></>}{token&&!detail.canEdit&&!detail.hidden&&<form className="flex flex-wrap gap-2" onSubmit={reportPost}><label className="sr-only" htmlFor="report-reason">신고 사유</label><select id="report-reason" className="field-input py-1" value={reportReason} onChange={e=>setReportReason(e.target.value)}><option value="SPAM">스팸</option><option value="ABUSE">욕설·혐오</option><option value="PERSONAL_INFO">개인정보</option><option value="OTHER">기타</option></select><input className="field-input w-40 py-1" aria-label="신고 상세 사유" value={reportDetails} maxLength={500} onChange={e=>setReportDetails(e.target.value)} placeholder="상세 사유 (선택)"/><button type="submit" className="secondary-button" disabled={busy}>신고</button></form>}{!token&&<a className="self-center text-sm text-blue-700 underline" href="/account">로그인 후 평가·신고 가능</a>}</div>
      <section className="mt-4" aria-labelledby="discussion-comments-heading"><h5 id="discussion-comments-heading" className="font-semibold">댓글 {comments.totalElements}</h5>{token?<form className="mt-2 flex flex-col gap-2 sm:flex-row" onSubmit={submitComment}><label className="sr-only" htmlFor="discussion-comment">댓글</label><input id="discussion-comment" className="field-input min-w-0 flex-1" value={comment} maxLength={500} onChange={e=>setComment(e.target.value)} placeholder="댓글을 입력하세요 (최대 500자)" required/><button type="submit" className="primary-button" disabled={busy||!comment.trim()}>댓글 등록</button></form>:<p className="mt-2 text-sm text-slate-600">댓글 작성은 로그인 후 이용할 수 있습니다.</p>}
        <ol className="mt-3 divide-y divide-slate-100" aria-label="댓글 목록">{comments.content.map(item=><li key={item.id} className="py-3"><div className="flex flex-wrap justify-between gap-2 text-xs text-slate-500"><strong className="text-slate-700">{item.nickname}</strong><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString('ko-KR')}</time></div>{editingComment===item.id?<div className="mt-2 flex gap-2"><input className="field-input min-w-0 flex-1" aria-label="댓글 수정" value={editCommentText} maxLength={500} onChange={e=>setEditCommentText(e.target.value)}/><button className="primary-button" type="button" onClick={()=>void saveComment(item.id)}>저장</button><button className="secondary-button" type="button" onClick={()=>setEditingComment(null)}>취소</button></div>:<><p className="mt-1 whitespace-pre-wrap break-words text-sm leading-6 text-slate-800">{item.content}</p>{item.canEdit&&<div className="mt-2 flex gap-2"><button className="secondary-button py-1" type="button" onClick={()=>{setEditingComment(item.id);setEditCommentText(item.content)}}>댓글 수정</button><button className="secondary-button py-1" type="button" disabled={busy} onClick={()=>void removeComment(item.id)}>댓글 삭제</button></div>}</>}</li>)}</ol>
        {comments.totalElements===0&&<p className="mt-3 text-sm text-slate-500">첫 댓글을 남겨 보세요.</p>}
        {comments.totalPages>1&&<nav className="mt-3 flex items-center justify-between" aria-label="댓글 페이지"><button className="secondary-button" type="button" disabled={commentPage===0} onClick={()=>setCommentPage(x=>x-1)}>이전 댓글</button><span className="text-sm text-slate-600">{commentPage+1} / {comments.totalPages}</span><button className="secondary-button" type="button" disabled={commentPage+1>=comments.totalPages} onClick={()=>setCommentPage(x=>x+1)}>다음 댓글</button></nav>}
      </section></>}
      <div className="mt-5 grid gap-4 border-t border-slate-200 pt-4"><button type="button" className="primary-button w-fit" onClick={()=>{setSelectedId(null);document.getElementById('market-terminal')?.scrollIntoView({behavior:'smooth',block:'start'})}}>{symbol} 거래 화면으로 이동</button><section aria-label="토론방 글 목록"><h5 className="font-semibold">토론방 목록</h5><ol className="mt-2 divide-y divide-slate-100">{result.content.map(post=><li key={post.id} className="flex flex-wrap items-center gap-x-3 py-2 text-sm"><span className="w-10 text-slate-500">{post.number}</span>{post.id===selectedId?<span aria-current="page" className="min-w-0 flex-1 font-semibold">{post.title} (현재 글)</span>:<button type="button" className="min-w-0 flex-1 truncate text-left text-blue-700" onClick={()=>{setSelectedId(post.id);setDetail(null);setCommentPage(0);setReveal(false)}}>{post.title}</button>}</li>)}</ol></section></div>
    </>}</div>:<div className="mt-4 overflow-x-auto border-t border-slate-200">{loading?<p className="py-4 text-sm text-slate-500" role="status">게시글을 불러오는 중입니다.</p>:<table className="w-full min-w-[34rem] text-left text-sm"><thead className="border-b border-slate-200 text-xs text-slate-500"><tr><th className="py-2 pr-2">글번호</th><th className="py-2 pr-2">제목</th><th className="py-2 pr-2">작성자</th><th className="py-2 pr-2">작성일</th><th className="py-2 text-right">추천</th></tr></thead><tbody>{result.content.map(post=><tr key={post.id} className="border-b border-slate-100"><td className="py-3 pr-2 text-slate-500">{post.number}</td><td className="max-w-[20rem] py-3 pr-2"><button type="button" className="truncate text-left font-semibold text-blue-700 hover:underline" onClick={()=>{setSelectedId(post.id);setDetail(null);setCommentPage(0);setReveal(false);setMessage('')}}>{post.title}</button></td><td className="py-3 pr-2">{post.nickname}</td><td className="whitespace-nowrap py-3 pr-2 text-xs text-slate-500">{new Date(post.createdAt).toLocaleDateString('ko-KR')}</td><td className="py-3 text-right tabular-nums">{post.upVotes}</td></tr>)}</tbody></table>}{!loading&&!result.content.length&&<p className="py-4 text-sm text-slate-500" role="status">등록된 글이 없습니다.</p>}</div>}
    {!selectedId&&<nav className="mt-3 flex items-center justify-between border-t border-slate-100 pt-3" aria-label="토론방 페이지"><button className="secondary-button" type="button" disabled={page<=0||loading} onClick={()=>setPage(x=>x-1)}>이전</button><span className="text-sm text-slate-600">{result.totalPages?page+1+' / '+result.totalPages:'0 / 0'}</span><button className="secondary-button" type="button" disabled={page+1>=result.totalPages||loading} onClick={()=>setPage(x=>x+1)}>다음</button></nav>}
  </section>
}


