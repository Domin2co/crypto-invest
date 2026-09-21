import { expect, test } from '@playwright/test'

test('shows the paper-only dashboard and explicit privacy consent controls', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: '투자 현황' })).toBeVisible()
  await expect(page.getByLabel('거래 모드: PAPER')).toHaveText('PAPER 모드')
  await expect(page.getByRole('link', { name: '회원가입 / 내 정보' })).toBeVisible()
  await expect(page.getByRole('checkbox', { name: /개인정보 처리에 동의합니다/ })).toHaveAttribute('required', '')
  await expect(page.getByRole('checkbox', { name: /마케팅 정보 수신/ })).not.toHaveAttribute('required', '')
  await expect(page.getByRole('link', { name: '개인정보처리방침' })).toHaveAttribute('href', '/privacy-policy.html')
  await expect(page.locator('[aria-label="KRW-BTC 공개 시세"] strong')).not.toHaveText(/불러오는 중|연동 대기/)
})

test('keeps primary navigation reachable at a 320px viewport', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 720 })
  await page.goto('/')

  await expect(page.getByRole('heading', { name: '회원가입과 개인정보' })).toBeVisible()
  await expect(page.getByRole('button', { name: '회원가입' })).toBeVisible()
  expect(await page.locator('nav').evaluate((node) => node.scrollWidth >= node.clientWidth)).toBeTruthy()
})

test('registers with required privacy consent and allows optional marketing withdrawal', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('이메일').fill(`e2e-${Date.now()}@example.com`)
  await page.getByLabel('비밀번호').fill('long-enough-password')
  await page.getByRole('checkbox', { name: /개인정보 처리에 동의합니다/ }).check()
  await page.getByRole('checkbox', { name: /마케팅 정보 수신/ }).check()
  const privacyLoad = page.waitForResponse((response) => response.url().includes('/api/privacy/me') && response.request().method() === 'GET')
  const [registration] = await Promise.all([
    page.waitForResponse((response) => response.url().includes('/api/auth/register')),
    page.getByRole('button', { name: '회원가입' }).click(),
  ])
  expect(registration.status()).toBe(200)
  expect((await privacyLoad).status()).toBe(200)

  await expect(page.getByText(/가입과 필수 개인정보 처리 동의가 완료/)).toBeVisible()
  await page.getByRole('button', { name: '마케팅 수신 철회' }).click()
  await expect(page.getByText('마케팅 수신 동의를 철회했습니다.')).toBeVisible()
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '계정 삭제' }).click()
  await expect(page.getByRole('button', { name: '회원가입' })).toBeVisible()
})
