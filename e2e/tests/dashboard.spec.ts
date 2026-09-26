import { expect, test } from '@playwright/test'

const menus = [
  ['실거래', '/trading', '로그인이 필요합니다'],
  ['모의거래', '/paper-trading', '모의거래'],
  ['포트폴리오', '/portfolio', '연동 포트폴리오'],
  ['시장', '/market', '주요 시세'],
  ['추천', '/recommendations', '시장 데이터에 따른 참고 정보'],
  ['거래 이력', '/history', '로그인이 필요합니다'],
] as const

async function mockPublicMarketStreams(page: import('@playwright/test').Page) {
  for (const [host, exchange] of [['api.upbit.com', 'UPBIT'], ['ws-api.bithumb.com', 'BITHUMB']] as const) {
    const response = await page.request.get('http://127.0.0.1:8080/api/markets/' + exchange + '/ticker?market=KRW-BTC')
    const { price } = await response.json() as { price: number }
    await page.routeWebSocket('wss://' + host + '/websocket/v1', (socket) => {
      let sequence = 0
      socket.onMessage((message) => {
        const requests = JSON.parse(String(message)) as Array<{ codes?: string[] }>
        const market = requests.flatMap((request) => request.codes ?? []).map((code) => code.split('.')[0]).find(Boolean) ?? 'KRW-BTC'
        const timestamp = Date.now()
        const rows = [
          { type: 'ticker', code: market, trade_price: price, opening_price: price * 0.99, high_price: price * 1.01, low_price: price * 0.98, signed_change_rate: 0.02, acc_trade_volume_24h: 123, trade_timestamp: timestamp },
          { type: 'trade', code: market, trade_price: price, trade_volume: 0.25, ask_bid: 'BID', sequential_id: host + '-' + (++sequence), trade_timestamp: timestamp },
          { type: 'trade', code: market, trade_price: price, trade_volume: 0.12345678, ask_bid: 'ASK', sequential_id: host + '-ask-' + sequence, trade_timestamp: timestamp },
          { type: 'orderbook', code: market, orderbook_units: Array.from({ length: 15 }, (_, index) => ({ ask_price: price + 1000 + index * 1000, ask_size: (index + 1) / 10, bid_price: price - 1000 - index * 1000, bid_size: (index + 2) / 10 })) },
        ]
        rows.forEach((row) => socket.send(JSON.stringify(row)))
      })
    })
  }
}

test.beforeEach(async ({ page }) => mockPublicMarketStreams(page))
test('each main menu opens its own page and the styled privacy page stays in the footer', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveTitle('Crypto Invest')
  await expect(page.getByRole('heading', { name: '내 자산과 시장을 차분히 살펴보세요' })).toBeVisible()
  await expect(page.locator('#main-content').getByText(/가상 잔액|모의 주문|PAPER|모의거래/)).toHaveCount(0)
  await expect(page.getByRole('link', { name: '개인정보 처리방침' })).toHaveAttribute('href', '/privacy')
  await expect(page.getByRole('link', { name: '포트폴리오 보기' })).toHaveCount(0)
  for (const [label, path, heading] of menus) {
    await page.getByRole('link', { name: label, exact: true }).click()
    await expect(page).toHaveTitle(label === '대시보드' ? 'Crypto Invest' : label + ' | Crypto Invest')
    await expect(page).toHaveURL(new RegExp(`${path}$`))
    if (path === '/trading') await expect(page.getByText(heading)).toBeVisible()
    else await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
    if (path === '/market') { await expect.poll(async () => page.getByRole('combobox', { name: '차트 종목' }).locator('option').count()).toBeGreaterThan(50); await expect(page.getByRole('heading', { name: 'BTC 일별 거래 통계' })).toBeVisible(); await expect(page.getByRole('heading', { name: 'BTC 종목 토론방' })).toBeVisible() }
    if (path === '/trading' || path === '/paper-trading') {
      await expect(page.getByRole('heading', { name: '실시간 시장 체결' })).toBeVisible()
      await expect(page.getByRole('img', { name: /KRW-BTC 1d candle chart/ })).toBeVisible()
      if (path === '/trading') { const tip = page.getByRole('note').filter({ hasText: '차트에 마우스 휠을 굴리거나' }); await expect(tip).toBeVisible(); await tip.getByRole('button', { name: '차트 확대 안내 닫기' }).click(); await expect(tip).toHaveCount(0) }
      const chartSvg = page.getByRole('img', { name: /KRW-BTC 1d candle chart/ }); await chartSvg.hover(); const zoomBefore = Number(await chartSvg.getAttribute('data-zoom')); const scrollBefore = await page.evaluate(() => window.scrollY); await page.mouse.wheel(0, -120); await expect.poll(async () => Number(await chartSvg.getAttribute('data-zoom'))).toBeGreaterThan(zoomBefore); expect(await page.evaluate(() => window.scrollY)).toBe(scrollBefore)
      await expect(page.getByRole('button', { name: '1분' })).toBeVisible()
      await expect(page.getByRole('radio', { name: '선' })).toBeVisible()
      await expect(page.getByRole('button', { name: '일반호가' })).toBeVisible()
      await expect(page.getByText('시가 대비')).toBeVisible()
      await expect(page.getByText('+1.01%', { exact: true }).first()).toBeVisible()
      await expect(page.getByRole('button', { name: '누적호가' })).toBeVisible()
      await expect(page.getByRole('button', { name: '일반호가' })).toHaveAttribute('aria-pressed', 'true')
      await expect(page.getByRole('button', { name: '일반호가' })).toHaveClass(/bg-slate-900/)
      await page.getByRole('button', { name: '누적호가' }).click()
      await expect(page.getByRole('button', { name: '누적호가' })).toHaveAttribute('aria-pressed', 'true')
      await expect(page.getByRole('button', { name: '누적호가' })).toHaveClass(/bg-slate-900/)
      await page.getByRole('button', { name: '일반호가' }).click()
      const chartArea = await page.getByRole('region', { name: 'Market chart' }).boundingBox()
      const orderbook = await page.getByRole('region', { name: '호가', exact: true }).boundingBox()
      const orderMenu = await page.locator('[aria-label="주문 메뉴"]').boundingBox()
      const marketTrades = await page.getByRole('heading', { name: '실시간 시장 체결' }).locator('..').locator('..').boundingBox()
      const orderAndTrades = await page.getByRole('complementary', { name: 'Orders and public trades' }).boundingBox()
      expect(Math.abs((marketTrades?.width ?? 0) - (orderMenu?.width ?? 0))).toBeLessThan(2)
      expect(Math.abs((orderAndTrades?.height ?? 0) - (orderbook?.height ?? 0))).toBeLessThan(2)
      const bookViewport = page.getByLabel('Price levels')
      await expect.poll(async () => bookViewport.evaluate((element) => getComputedStyle(element).overflowAnchor)).toBe('none')
      await expect.poll(async () => bookViewport.evaluate((element) => element.scrollTop)).toBe(280)
      const centeredLevels = await bookViewport.evaluate((element) => {
        const bounds = element.getBoundingClientRect()
        const visible = (name: string) => [...element.querySelectorAll('[data-testid="' + name + '"]')].filter((node) => {
          const row = node.getBoundingClientRect()
          return row.top >= bounds.top && row.bottom <= bounds.bottom
        }).length
        return { asks: visible('ask-quantity'), bids: visible('bid-quantity'), scrollHeight: element.scrollHeight, clientHeight: element.clientHeight }
      })
      expect(centeredLevels).toMatchObject({ asks: 8, bids: 8, clientHeight: 680 })
      expect(centeredLevels.scrollHeight).toBeGreaterThan(centeredLevels.clientHeight)
      await bookViewport.evaluate((element) => { element.scrollTop = element.scrollHeight })
      await expect.poll(async () => bookViewport.evaluate((element) => element.scrollTop)).toBeGreaterThan(280)
      await bookViewport.evaluate((element) => { element.scrollTop = 280 })
      await bookViewport.evaluate((element) => { const inserted = document.createElement('div'); inserted.style.cssText = 'height:40px;grid-column:1 / -1'; element.firstElementChild?.prepend(inserted) })
      await expect.poll(async () => bookViewport.evaluate((element) => element.scrollTop)).toBe(280)
      await bookViewport.evaluate((element) => element.firstElementChild?.firstElementChild?.remove())
      await expect.poll(async () => bookViewport.evaluate((element) => element.scrollTop)).toBe(280)
      expect(orderbook?.y).toBeGreaterThan(chartArea?.y ?? 0)
      expect(chartArea?.width).toBeGreaterThan(orderbook?.width ?? 0)
      expect(Math.abs((orderMenu?.y ?? 0) - (orderbook?.y ?? 0))).toBeLessThan(12)
      expect(orderMenu?.x).toBeGreaterThan(orderbook?.x ?? 0)
      await expect(page.getByText('공개 시장 전체 · 내 주문 내역 아님')).toBeVisible()
      await expect.poll(async () => page.getByRole('combobox', { name: '차트 종목' }).locator('option').count()).toBeGreaterThan(50)
      await expect(page.getByText('실시간 연결').first()).toBeVisible({ timeout: 15000 })
      await expect(page.getByText('실시간 연결')).toHaveCount(2, { timeout: 15000 })
      const buyFill = page.getByLabel('매수 체결량').first()
      const sellFill = page.getByLabel('매도 체결량').first()
      await expect(buyFill).toHaveText('0.25 BTC')
      await expect(buyFill).toHaveClass(/bg-rose-100/)
      expect(await buyFill.evaluate((element) => element.parentElement?.className)).toContain('grid-cols-[minmax(0,1fr)_5.5rem_6.5rem]')
      expect(await sellFill.evaluate((element) => element.parentElement?.className)).toContain('grid-cols-[minmax(0,1fr)_5.5rem_6.5rem]')
      await expect(sellFill).toHaveText('0.1235 BTC')
      await expect(sellFill).toHaveClass(/bg-blue-100/)
    }
    if (path === '/trading') {
      await expect(page.getByText(/시장 시세는 공개 정보입니다/)).toBeVisible()
    }
    if (path === '/paper-trading') {
      await expect(page.locator('#paper-league-heading')).toBeVisible()
      await expect(page.getByText('대회 시작 또는 첫 체결을 기다리는 중입니다.')).toBeVisible()
    }
    await expect(page.getByRole('link', { name: '개인정보 처리방침' })).toBeVisible()
  }
  await page.getByRole('link', { name: '개인정보 처리방침' }).click()
  await expect(page).toHaveURL(/\/privacy$/)
  await expect(page).toHaveTitle('개인정보 처리방침 | Crypto Invest')
  await expect(page.locator('article.panel-card')).toBeVisible()
})

test('navigation and privacy footer remain usable at 320px', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 720 })
  await page.goto('/')
  await expect(page.getByRole('navigation', { name: '주요 메뉴' })).toBeVisible()
  await page.getByRole('link', { name: '시장', exact: true }).click()
  await expect(page).toHaveURL(/\/market$/)
  await expect(page.getByRole('link', { name: '개인정보 처리방침' })).toBeVisible()
})

test('registers and logs into a real test account without calling a live-order route', async ({ page }) => {
  const email = `paper-e2e-${Date.now()}@example.com`
  await page.goto('/account')
  await page.getByRole('tab', { name: '회원가입' }).click()
  await page.getByLabel('이메일').fill(email)
  await page.locator('input[name="password"]').fill('Good-pass1!')
  await page.getByRole('button', { name: '인증 코드 받기' }).click()
  const findMessage = async () => {
    const mailbox = await page.request.get('http://127.0.0.1:8025/api/v1/messages?limit=100')
    expect(mailbox.ok()).toBeTruthy()
    const summaries = (await mailbox.json()).messages as Array<{ ID: string; Created: string; To: Array<{ Address: string }> }>
    return summaries.filter((item) => item.To.some((recipient) => recipient.Address.toLowerCase() === email.toLowerCase())).sort((left, right) => Date.parse(right.Created) - Date.parse(left.Created))[0]
  }
  await expect.poll(findMessage, { timeout: 10_000 }).toBeDefined()
  const message = await findMessage()
  expect(message).toBeDefined()
  const emailMessage = await page.request.get(`http://127.0.0.1:8025/api/v1/message/${message!.ID}`)
  const verificationText = (await emailMessage.json()).Text as string
  const code = verificationText.match(/인증 코드: (\d{6})/)?.[1]
  expect(code).toBeTruthy()
  await page.getByLabel('이메일 인증 코드').fill(code!)
  await page.getByRole('button', { name: '인증 확인' }).click()
  await expect(page.getByText('이메일 인증 완료')).toBeVisible()
  await page.getByRole('checkbox', { name: /개인정보 처리에 동의합니다/ }).check()
  const [registration] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/auth/register')),
    page.getByRole('button', { name: '회원가입' }).click(),
  ])
  expect(registration.status()).toBe(200)
  let accessToken = (await registration.json()).accessToken as string
  const nickname = `P${Date.now().toString().slice(-7)}`
  await expect(page.getByRole('dialog', { name: '사용할 닉네임을 정해 주세요' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: '주요 메뉴' })).toHaveCount(0)
  const blockedStatus = await page.evaluate(async (token) => (await fetch('/api/paper/orders/summary', { headers: { Authorization: `Bearer ${token}` } })).status, accessToken)
  expect(blockedStatus).toBe(403)
  await page.getByRole('textbox', { name: '닉네임' }).fill(nickname)
  const [availability] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/account/nickname/availability')),
    page.getByRole('button', { name: '중복확인' }).click(),
  ])
  expect((await availability.json()).available).toBe(true)
  const [nicknameSave] = await Promise.all([
    page.waitForResponse((response) => response.url().endsWith('/api/account/nickname') && response.request().method() === 'POST'),
    page.getByRole('button', { name: '저장', exact: true }).click(),
  ])
  expect(nicknameSave.status()).toBe(204)
  await expect(page.getByText(`로그인 계정: ${email}`)).toBeVisible()
  expect(await page.evaluate(() => localStorage.length)).toBe(0)
  expect(await page.evaluate(() => sessionStorage.getItem('crypto-invest-token'))).toBe(accessToken)
  await page.getByRole('button', { name: '로그아웃' }).click()
  await page.getByRole('tab', { name: '로그인' }).click()
  await page.getByLabel('이메일').fill(email)
  await page.locator('input[name="password"]').fill('Good-pass1!')
  await page.getByRole('button', { name: '\uBE44\uBC00\uBC88\uD638\uB97C \uC78A\uC73C\uC168\uB098\uC694?' }).click()
  const previousMail = await findMessage()
  await page.getByRole('button', { name: '\uC778\uC99D \uCF54\uB4DC \uBC1B\uAE30' }).click()
  await expect.poll(async () => (await findMessage())?.ID, { timeout: 10_000 }).not.toBe(previousMail?.ID)
  const resetMessage = await findMessage()
  const resetEmailMessage = await page.request.get(`http://127.0.0.1:8025/api/v1/message/${resetMessage!.ID}`)
  const resetCode = ((await resetEmailMessage.json()).Text as string).match(/\uC778\uC99D \uCF54\uB4DC: (\d{6})/)?.[1]
  expect(resetCode).toBeTruthy()
  await page.getByLabel('\uC774\uBA54\uC77C \uC778\uC99D \uCF54\uB4DC').fill(resetCode!)
  await page.getByRole('button', { name: '\uCF54\uB4DC \uD655\uC778' }).click()
  await page.locator('input[name="newPassword"]').fill('Reset-pass2!')
  await page.getByRole('button', { name: '\uBE44\uBC00\uBC88\uD638 \uBCC0\uACBD' }).click()
  await expect(page.getByText('Password updated. Please sign in.')).toBeVisible()
  const revokedSessionStatus = await page.evaluate(async (token) => (await fetch('/api/account/profile', { headers: { Authorization: `Bearer ${token}` } })).status, accessToken)
  expect(revokedSessionStatus).toBe(401)
  await page.locator('input[name="email"]').fill(email)
  await page.locator('input[name="password"]').fill('Reset-pass2!')
  const [login] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/auth/login')),
    page.getByRole('button', { name: '로그인' }).click(),
  ])
  expect(login.status()).toBe(200)
  await expect(page.getByRole('heading', { name: '내 자산과 시장을 차분히 살펴보세요' })).toBeVisible()
  accessToken = (await login.json()).accessToken as string
  await page.reload()
  await expect(page.getByRole('heading', { name: '내 자산과 시장을 차분히 살펴보세요' })).toBeVisible()
  await page.getByRole('link', { name: '마이 페이지' }).click()
  await expect(page.getByText('로그인 계정: ' + email)).toBeVisible()
  await page.getByRole('button', { name: '내 정보 수정' }).click()
  const changedNickname = `Q${Date.now().toString().slice(-7)}`
  await page.getByRole('textbox', { name: '닉네임' }).fill(changedNickname)
  await expect(page.getByRole('button', { name: '저장', exact: true })).toBeDisabled()
  const [editAvailability] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/account/nickname/availability')),
    page.getByRole('button', { name: '중복확인' }).click(),
  ])
  expect((await editAvailability.json()).available).toBe(true)
  const [editNickname] = await Promise.all([
    page.waitForResponse((response) => response.url().endsWith('/api/account/nickname') && response.request().method() === 'POST'),
    page.getByRole('button', { name: '저장', exact: true }).click(),
  ])
  expect(editNickname.status()).toBe(204)
  await page.getByRole('button', { name: '마이 페이지로 돌아가기' }).click()
  await expect(page.getByText(`현재 닉네임: ${changedNickname}`)).toBeVisible()
  await page.getByRole('link', { name: '시장', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'BTC 종목 토론방' })).toBeVisible()
  const discussionText = `종목 토론 ${Date.now()}`
  await page.getByLabel('제목').fill('BTC discussion title')
  await page.getByRole('textbox', { name: '토론 내용' }).fill(discussionText)
  await page.getByLabel('글꼴').selectOption('serif')
  await page.getByLabel('크기').selectOption('20')
  await page.getByLabel('정렬').selectOption('center')
  await page.locator('input[type="file"][accept="image/png,image/jpeg,image/webp"]').setInputFiles({
    name: 'pixel.png',
    mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a+XcAAAAASUVORK5CYII=', 'base64'),
  })
  await expect(page.getByText('이미지 첨부됨')).toBeVisible()
  const [discussionPost] = await Promise.all([
    page.waitForResponse((response) => response.url().endsWith('/api/discussions/BTC') && response.request().method() === 'POST'),
    page.getByRole('button', { name: '게시글 등록' }).click(),
  ])
  expect(discussionPost.status()).toBe(201)
  const discussionListResponse = await page.request.get("http://127.0.0.1:8080/api/discussions/BTC")
  const discussionList = await discussionListResponse.json() as { content: Array<{ id: string; title: string }> }
  const discussionPostId = discussionList.content.find((post) => post.title === "BTC discussion title")!.id
  await page.getByRole('button', { name: 'BTC discussion title' }).first().click()
  const formattedContent = page.getByText(discussionText)
  await expect(formattedContent).toBeVisible()
  await expect.poll(() => formattedContent.evaluate(node => getComputedStyle(node).fontSize)).toBe('20px')
  await expect.poll(() => formattedContent.evaluate(node => getComputedStyle(node).textAlign)).toBe('center')
  const attachment = page.getByRole('img', { name: '게시글 첨부 이미지' })
  await expect(attachment).toBeVisible()
  await expect.poll(() => attachment.evaluate(node => (node as HTMLImageElement).naturalWidth)).toBe(1)
  await page.getByRole('textbox', { name: '댓글' }).fill('E2E discussion comment')
  const [commentResponse] = await Promise.all([
    page.waitForResponse((response) => response.url().endsWith('/api/discussions/BTC/' + discussionPostId + '/comments')),
    page.getByRole('button', { name: '댓글 등록' }).click(),
  ])
  expect(commentResponse.status()).toBe(201)
  await expect(page.getByText('E2E discussion comment')).toBeVisible()
  const [voteResponse] = await Promise.all([
    page.waitForResponse((response) => response.url().endsWith('/api/discussions/BTC/' + discussionPostId + '/vote')),
    page.getByRole('button', { name: '추천 0', exact: true }).click(),
  ])
  expect(voteResponse.status()).toBe(200)
  await expect(page.getByRole('button', { name: '추천 1', exact: true })).toBeVisible()
  const publicDiscussion = await page.evaluate(async (id) => {
    const response = await fetch('/api/discussions/BTC')
    const payload = await response.json() as { content: Array<{ id: string; title: string; nickname: string; content?: string }> }
    const summary = payload.content.find((post) => post.id === id)!
    const detail = await (await fetch('/api/discussions/BTC/' + id)).json() as { content: string }
    return { status: response.status, summaryHasNoContent: !('content' in summary), detailContent: detail.content, nickname: summary.nickname }
  }, discussionPostId)
  expect(publicDiscussion.status).toBe(200)
  expect(publicDiscussion.summaryHasNoContent).toBe(true)
  expect(publicDiscussion.detailContent).toBe(discussionText)
  expect(publicDiscussion.nickname).toBe(changedNickname)
  await page.getByRole('link', { name: '마이 페이지' }).click()
  const [leaderboardConsent] = await Promise.all([
    page.waitForResponse((response) => response.url().endsWith('/api/privacy/paper-leaderboard-consent') && response.request().method() === 'PATCH'),
    page.getByRole('button', { name: '랭킹 공개 선택 동의' }).click(),
  ])
  expect(leaderboardConsent.status()).toBe(204)
  await page.getByRole('link', { name: '모의거래', exact: true }).click()
  const [enrollment] = await Promise.all([
    page.waitForResponse((response) => response.url().endsWith('/api/paper-league/entry') && response.request().method() === 'POST'),
    page.getByRole('button', { name: /대회 참가 신청$/ }).click(),
  ])
  expect(enrollment.status()).toBe(200)
  await expect(page.getByText(/대회 참가 신청 완료/)).toBeVisible()
  await page.getByRole('link', { name: '마이 페이지', exact: true }).click()
  const [withdrawal] = await Promise.all([
    page.waitForResponse((response) => response.url().endsWith('/api/privacy/paper-leaderboard-consent') && response.request().method() === 'PATCH'),
    page.getByRole('button', { name: '랭킹 공개 동의 철회' }).click(),
  ])
  expect(withdrawal.status()).toBe(204)
  await page.getByRole('link', { name: '모의거래', exact: true }).click()
  await expect(page.getByRole('heading', { name: '종목 시장 평가' })).toBeVisible()
  await expect(page.locator('text=₩').first()).toBeVisible()
  await page.getByRole('link', { name: '\uC2E4\uAC70\uB798', exact: true }).click()
  await expect(page.getByText('실거래 주문이 비활성화되어 있습니다')).toBeVisible()
  await page.getByRole('tab', { name: '호가주문' }).click()
  await expect(page.getByText('실거래 주문이 비활성화되어 있습니다')).toBeVisible()
  await expect(page.getByRole('button', { name: '\uC2E4\uC81C \uC8FC\uBB38 \uAC80\uD1A0' })).toHaveCount(0)
  await page.getByRole('link', { name: '\uBAA8\uC758\uAC70\uB798', exact: true }).click()
  await expect(page.getByRole('heading', { name: '\uC885\uBAA9 \uC2DC\uC7A5 \uD3C9\uAC00' })).toBeVisible()
  const [buy] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/paper/orders') && response.request().method() === 'POST'),
    page.locator('form[role=tabpanel] button[type=submit]').click(),
  ])
  expect(buy.status()).toBe(201)
  const fill = await buy.json()
  expect(fill.quantity).toBeGreaterThan(0)
  await expect(page.getByRole('status').last()).toContainText('모의거래 체결: 업비트 매수 BTC')
  await page.getByRole('tab', { name: '매도' }).click()
  await page.locator('form input[type=number]').last().fill(String(fill.quantity))
  const [sell] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/paper/orders') && response.request().method() === 'POST'),
    page.locator('form[role=tabpanel] button[type=submit]').click(),
  ])
  expect(sell.status()).toBe(201)
  await expect(page.getByRole('status').last()).toContainText('모의거래 체결: 업비트 매도 BTC')
  await page.getByRole('tab', { name: '간편주문' }).click()
  await page.getByRole('button', { name: '매수', exact: true }).click()
  const [quickBuy] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/paper/orders') && response.request().method() === 'POST'),
    page.getByRole('button', { name: '간편 주문 실행' }).click(),
  ])
  expect(quickBuy.status()).toBe(201)
  await page.getByRole('button', { name: /매도 호가/ }).first().click()
  await expect(page.getByRole('tab', { name: '호가주문' })).toHaveAttribute('aria-selected', 'true')
  await page.getByLabel('호가 지정 가격(KRW)').fill('1000000000')
  const [orderbookBuy] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/paper/orders') && response.request().method() === 'POST'),
    page.getByRole('button', { name: '호가 주문 실행' }).click(),
  ])
  expect(orderbookBuy.status()).toBe(201)
  await page.getByRole('button', { name: /빗썸 · KRW-BTC/ }).click()
  await expect(page.getByRole('button', { name: /빗썸 · KRW-BTC/ })).toContainText('₩')
  await page.getByRole('tab', { name: '매수' }).click()
  const [bithumbBuy] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/paper/orders') && response.request().method() === 'POST'),
    page.locator('form[role=tabpanel] button[type=submit]').click(),
  ])
  expect(bithumbBuy.status()).toBe(201)
  expect((await bithumbBuy.json()).exchange).toBe('BITHUMB')
  await expect(page.getByRole('status').last()).toContainText('모의거래 체결: 빗썸 매수 BTC')
  await page.getByRole('link', { name: '거래 이력', exact: true }).click()
  await expect(page.getByRole('heading', { name: '모의 주문 내역' })).toBeVisible()
  await expect(page.getByText(/빗썸 모의거래 · KRW/)).toBeVisible()
  const cleanupStatus = await page.evaluate(async (token) => (await fetch('/api/privacy/me', {
    method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
  })).status, accessToken)
  expect(cleanupStatus).toBe(204)
})

test('recommendation page explains score, confidence, regime and unavailable metrics', async ({ page }) => {
  await page.route('**/api/recommendations/UPBIT?market=KRW-BTC', (route) => route.fulfill({ json: {
    recommendation: { symbol: 'KRW-BTC', score: 50, signal: 'HOLD', targetWeight: 0, reasons: [] },
    generatedAt: '2026-09-26T00:00:00Z', dataCapturedAt: '2026-09-25T00:00:00Z', dataSource: 'UPBIT public daily candles', candleCount: 121,
    indicators: { rsi: 54, momentum: 100, volatilityPercent: 2 }, limitations: ['미확보 데이터 제외'],
    evaluation: { ruleVersion: 'rules-1.0', score: 52, confidence: 79, rating: 'BUY', marketRegime: { regime: 'RISK_ON' },
      factors: [{ code: 'MVRV', explanation: 'MVRV 데이터 미확보', points: 0, dataStatus: 'UNAVAILABLE' }], metrics: {} },
  } }))
  await page.goto('/recommendations')
  await expect(page.getByText('BUY · +52점 · 신뢰도 79%')).toBeVisible()
  await expect(page.getByText('시장 상태: RISK_ON')).toBeVisible()
  await expect(page.getByText(/MVRV 데이터 미확보/)).toBeVisible()
})