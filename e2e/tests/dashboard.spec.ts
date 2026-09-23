import { expect, test } from '@playwright/test'

const menus = [
  ['포트폴리오', '/portfolio', '연동 포트폴리오'],
  ['시장', '/market', '주요 시세'],
  ['추천', '/recommendations', '시장 데이터에 따른 참고 정보'],
  ['거래 이력', '/history', '로그인이 필요합니다'],
] as const

test('each main menu opens its own page and the styled privacy page stays in the footer', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('정상')).toBeVisible()
  await expect(page.getByLabel('현재 거래 모드 상태: PAPER')).toBeVisible()
  await expect(page.getByRole('link', { name: '개인정보 처리방침' })).toHaveAttribute('href', '/privacy')
  await expect(page.getByRole('link', { name: '포트폴리오 보기' })).toHaveCount(0)
  for (const [label, path, heading] of menus) {
    await page.getByRole('link', { name: label, exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`${path}$`))
    await expect(page.getByRole('heading', { name: heading })).toBeVisible()
    await expect(page.getByRole('link', { name: '개인정보 처리방침' })).toBeVisible()
  }
  await page.getByRole('link', { name: '개인정보 처리방침' }).click()
  await expect(page).toHaveURL(/\/privacy$/)
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
  await page.getByLabel('비밀번호').fill('long-enough-password')
  await page.getByRole('checkbox', { name: /개인정보 처리에 동의합니다/ }).check()
  const [registration] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/auth/register')),
    page.getByRole('button', { name: '회원가입' }).click(),
  ])
  expect(registration.status()).toBe(200)
  await expect(page.getByText(`로그인 계정: ${email}`)).toBeVisible()
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0)
  await page.getByRole('button', { name: '로그아웃' }).click()
  await page.getByRole('tab', { name: '로그인' }).click()
  await page.getByLabel('이메일').fill(email)
  await page.getByLabel('비밀번호').fill('long-enough-password')
  const [login] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/auth/login')),
    page.getByRole('button', { name: '로그인' }).click(),
  ])
  expect(login.status()).toBe(200)
  await expect(page.getByText(`로그인 계정: ${email}`)).toBeVisible()
  await page.getByRole('link', { name: '대시보드', exact: true }).click()
  const [buy] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/paper/orders') && response.request().method() === 'POST'),
    page.getByRole('button', { name: 'PAPER 주문 실행' }).click(),
  ])
  expect(buy.status()).toBe(201)
  await expect(page.getByText(/PAPER 체결 완료: BUY BTC/)).toBeVisible()
  await page.getByLabel('방향').selectOption('SELL')
  await page.getByLabel('매도 수량(BTC)').fill('0.0001')
  const [sell] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/paper/orders') && response.request().method() === 'POST'),
    page.getByRole('button', { name: 'PAPER 주문 실행' }).click(),
  ])
  expect(sell.status()).toBe(201)
  await expect(page.getByText(/PAPER 체결 완료: SELL BTC/)).toBeVisible()
  await page.getByRole('link', { name: '거래 이력', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'PAPER 주문 내역' })).toBeVisible()
})
