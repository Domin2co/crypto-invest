export default function PrivacyPolicyPage() {
  return <article className="panel-card max-w-4xl" aria-labelledby="privacy-title">
    <p className="eyebrow">개인정보 보호</p>
    <h2 className="panel-title" id="privacy-title">개인정보 처리방침</h2>
    <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950">
      <strong>개발용 초안</strong><p className="mt-1">현재 문서는 개발·테스트용입니다. 공개 또는 유료 서비스 전에 사업자, 개인정보 보호책임자, 문의처, 보유기간, 수탁·국외이전 정보를 확정하고 법률 검토를 거쳐야 합니다.</p>
    </div>
    <div className="mt-8 space-y-8 text-[15px] leading-7 text-slate-700">
      <section><h3 className="section-title">1. 처리 목적과 항목</h3><ul className="mt-3 list-disc space-y-2 pl-6"><li><strong>회원 인증:</strong> 이메일과 암호화된 비밀번호 해시를 계정 인증과 보호에 사용합니다.</li><li><strong>거래소 연동:</strong> 사용자가 요청한 자산 조회를 위해 거래소 API 접근 키를 암호화해 저장합니다. 키와 서명은 화면·응답·로그에 표시하지 않습니다.</li><li><strong>마케팅:</strong> 선택 동의 여부, 정책 버전, 동의·철회 시각을 기록합니다. 동의하지 않아도 가입과 핵심 기능을 사용할 수 있습니다.</li><li><strong>보안·감사:</strong> 부정 사용 방지와 사고 대응에 필요한 최소 주문 식별자·상태·오류 코드를 기록합니다.</li></ul></section>
      <section><h3 className="section-title">2. 보유와 파기</h3><p className="mt-3">계정 정보는 탈퇴 또는 법률상 보존 의무가 발생할 때까지 처리합니다. 거래소 연동을 해제하거나 계정을 삭제하면 저장된 거래소 자격증명을 삭제합니다. 법정 보존 대상과 구체적인 기간은 정식 서비스 전에 확정해 고지합니다.</p></section>
      <section><h3 className="section-title">3. 이용자 권리</h3><p className="mt-3">로그인 후 계정 화면에서 본인 정보와 동의 이력을 확인하고 마케팅 동의를 철회하거나 계정을 삭제할 수 있습니다. 거래소 API 키, 비밀번호, 서명은 열람·내보내기 대상에 포함하지 않습니다. 정정·처리정지 요청과 문의처는 정식 정책에 안내할 예정입니다.</p></section>
      <section><h3 className="section-title">4. 안전 조치</h3><p className="mt-3">비밀번호는 해시 처리하고 거래소 자격증명은 AES-256-GCM으로 암호화해 저장합니다. 접근 토큰은 브라우저 저장소에 기록하지 않고 현재 앱 메모리에서만 사용합니다.</p></section>
      <section><h3 className="section-title">5. 마케팅 동의</h3><p className="mt-3">마케팅 수신은 선택 항목입니다. 동의 여부와 정책 버전·시각을 기록하며, 계정 화면에서 언제든 철회할 수 있습니다.</p></section>
      <section><h3 className="section-title">6. 방침 변경</h3><p className="mt-3">처리 목적이나 항목 등 중요한 내용이 바뀌면 변경 내용을 알리고 필요한 경우 다시 동의를 받습니다. 현재 적용 버전은 2026-09-21 입니다.</p></section>
    </div>
  </article>
}