import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import MarketDiscussionBoard from './MarketDiscussionBoard'

describe('MarketDiscussionBoard', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })))))
  afterEach(() => { cleanup(); vi.unstubAllGlobals() })

  it('shows only the list fields and loads full text on title selection', async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(new Response(JSON.stringify({
        content: [{ id: '1', number: 1, symbol: 'BTC', nickname: 'reader', title: 'BTC update', createdAt: '2026-09-24T00:00:00Z', upVotes: 2 }],
        page: 0, size: 10, totalElements: 1, totalPages: 1,
      })))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        id: '1', symbol: 'BTC', nickname: 'reader', title: 'BTC update', content: 'Market note',
        fontFamily: 'system', fontSize: 16, textAlign: 'left', hasImage: false,
        createdAt: '2026-09-24T00:00:00Z', upVotes: 2, downVotes: 0, blind: false, userVote: null, comments: [],
      })))
    render(<MarketDiscussionBoard symbol="BTC" />)
    expect(await screen.findByRole('button', { name: 'BTC update' })).toBeInTheDocument()
    expect(screen.getByText('reader')).toBeInTheDocument()
    expect(screen.queryByText('Market note')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'BTC update' }))
    expect(await screen.findByText('Market note')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '토론방 목록' })).toBeInTheDocument()
  })

  it('converts the older array response into list summaries', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify([
      { id: '2', symbol: 'BTC', nickname: 'reader', content: 'Older market note', createdAt: '2026-09-24T00:00:00Z' },
    ])))
    render(<MarketDiscussionBoard symbol="BTC" />)
    expect(await screen.findByRole('button', { name: 'Older market note' })).toBeInTheDocument()
    expect(screen.getAllByText('전체 1개 · 페이지당 10개')).toHaveLength(1)
  })
})


describe('discussion comment tools', () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals() })

  it('pages comments and saves an author comment edit', async () => {
    const requests: Array<{ url: string; method: string; body?: string }> = []
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url=String(input); requests.push({url,method:init?.method??'GET',body:init?.body as string|undefined})
      if(url==='/api/discussions/BTC?page=0') return new Response(JSON.stringify({content:[{id:'p1',number:1,title:'Post',nickname:'owner',createdAt:'2026-09-24T00:00:00Z',upVotes:0}],page:0,size:10,totalElements:1,totalPages:1}))
      if(url==='/api/discussions/BTC/p1') return new Response(JSON.stringify({id:'p1',symbol:'BTC',nickname:'owner',title:'Post',content:'Body',fontFamily:'system',fontSize:16,textAlign:'left',hasImage:false,createdAt:'2026-09-24T00:00:00Z',updatedAt:'2026-09-24T00:00:00Z',upVotes:0,downVotes:0,blind:false,hidden:false,canEdit:false,userVote:null}))
      if(url.endsWith('/comments?page=1')) return new Response(JSON.stringify({content:[{id:'c11',nickname:'owner',content:'Second page',createdAt:'2026-09-24T00:01:00Z',canEdit:true}],page:1,size:10,totalElements:11,totalPages:2}))
      if(url.endsWith('/comments?page=0')) return new Response(JSON.stringify({content:[{id:'c1',nickname:'owner',content:'First page',createdAt:'2026-09-24T00:00:00Z',canEdit:true}],page:0,size:10,totalElements:11,totalPages:2}))
      return new Response(null,{status:204})
    }))
    render(<MarketDiscussionBoard symbol="BTC" token="session-token" />)
    fireEvent.click(await screen.findByRole('button',{name:'Post'}))
    expect(await screen.findByText('First page')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button',{name:'다음 댓글'}))
    expect(await screen.findByText('Second page')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button',{name:'댓글 수정'}))
    fireEvent.change(screen.getByRole('textbox',{name:'댓글 수정'}),{target:{value:'Updated comment'}})
    fireEvent.click(screen.getByRole('button',{name:'저장'}))
    expect(await screen.findByText('댓글을 수정했습니다.')).toBeInTheDocument()
    expect(requests.some(r=>r.method==='PATCH'&&r.url.endsWith('/comments/c11')&&JSON.parse(r.body??'{}').content==='Updated comment')).toBe(true)
  })
})