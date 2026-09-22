import { expect, test } from '@playwright/test'

test('shows the paper-only dashboard and explicit privacy consent controls', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: '투자 현황' })).toBeVisible()
  await expect(page.getByLabel('거래 모드: PAPER')).toHaveText('PAPER 모드')
  await expect(page.getByRole('link', { name: '회원가입 / 내 정보' })).toBeVisible()
  await expect(page.getByRole('checkbox', { name: /개인정보 처리에 동의합니다/ })).toHaveAttribute('required', '')
  await expect(page.getByRole('checkbox', { name: /마케팅 정보 수신/ })).not.toHaveAttribute('required', '')
  await expect(page.getByRole('link', { name: '개인정보처리방침' })).toHaveAttribute('href', '/privacy-policy.html')
  await expect(page.locator('[aria-label="KRW-BTC 공개 시세"] strong')).not.toHaveText(/불러오는 중|로딩 대기/)
  await expect(page.getByText(/데이터: UPBIT public daily candles/)).toBeVisible()
})

test('keeps primary navigation reachable at a 320px viewport', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 720 })
  await page.goto('/')

  await expect(page.getByRole('heading', { name: '회원가입과 개인정보' })).toBeVisible()
  await expect(page.getByRole('button', { name: '회원가입' })).toBeVisible()
  expect(await page.locator('nav').evaluate((node) => node.scrollWidth >= node.clientWidth)).toBeTruthy()
})

test('registers and fills PAPER buy and sell orders without a real exchange order', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('이메일').fill(`paper-e2e-${Date.now()}@example.com`)
  await page.getByLabel('비밀번호').fill('long-enough-password')
  await page.getByRole('checkbox', { name: /개인정보 처리에 동의합니다/ }).check()
  const privacyLoad = page.waitForResponse((response) => response.url().includes('/api/privacy/me') && response.request().method() === 'GET')
  const [registration] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/auth/register')),
    page.getByRole('button', { name: '회원가입' }).click(),
  ])
  expect(registration.status()).toBe(200)
  expect((await privacyLoad).status()).toBe(200)

  await page.getByLabel('접근 키').fill('e2e-access-key')
  await page.getByLabel('비밀 키').fill('e2e-secret-key')
  const [accountSaved] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/exchange-accounts') && response.request().method() === 'POST'),
    page.getByRole('button', { name: '암호화 저장' }).click(),
  ])
  expect(accountSaved.status()).toBe(204)
  await expect(page.getByText(/암호화해 저장했습니다/)).toBeVisible()

  const [paperOrder] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/paper/orders') && response.request().method() === 'POST'),
    page.getByRole('button', { name: 'PAPER 주문 실행' }).click(),
  ])
  expect(paperOrder.status()).toBe(201)
  await expect(page.getByText(/PAPER 체결 완료: BUY BTC/)).toBeVisible()

  await page.getByLabel('방향').selectOption('SELL')
  await page.getByLabel('매도 수량(BTC)').fill('0.0001')
  const [paperSell] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/paper/orders') && response.request().method() === 'POST'),
    page.getByRole('button', { name: 'PAPER 주문 실행' }).click(),
  ])
  expect(paperSell.status()).toBe(201)
  await expect(page.getByText(/PAPER 체결 완료: SELL BTC/)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'PAPER 주문 내역' })).toBeVisible()
  await expect(page.getByText(/SELL BTC.*FILLED/)).toBeVisible()
})
