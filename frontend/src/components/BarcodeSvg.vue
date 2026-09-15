<script setup>
/**
 * 바코드 한 개를 SVG 로 그린다.
 *
 * SVG 를 쓰는 이유는 인쇄 때문이다. 캔버스는 화면 해상도로 래스터화되어
 * 인쇄하면 바가 뭉개지는데, SVG 는 프린터 해상도로 다시 그려진다. 바코드는
 * 바 두께의 비율이 조금만 어긋나도 스캐너가 못 읽으므로 이 차이가 크다.
 *
 * 인코딩은 JsBarcode 에 맡긴다. Code128 은 107개짜리 패턴 표와 체크섬으로
 * 이루어지는데, 표를 한 줄만 잘못 적어도 '그럴듯하지만 안 읽히는' 바코드가
 * 나온다. 눈으로는 확인할 수 없는 종류의 오류라 검증된 구현을 쓴다.
 */
import { computed, onMounted, ref, watch } from 'vue'
import JsBarcode from 'jsbarcode'

const props = defineProps({
  /** 바코드에 담을 값 */
  value: { type: String, required: true },
  /**
   * 심볼로지.
   *   CODE128  영숫자. 빈 코드처럼 문자가 섞인 값에 쓴다.
   *   EAN13    숫자 13자리. 유통표준코드(880…)용.
   */
  format: { type: String, default: 'CODE128' },
  /** 가는 바 하나의 너비(mm). 작을수록 바코드가 짧아지고 읽기 어려워진다. */
  moduleWidth: { type: Number, default: 0.33 },
  /** 바 높이(mm) */
  height: { type: Number, default: 12 },
  /** 바 아래에 값을 글자로 찍을지 */
  showText: { type: Boolean, default: true },
  fontSize: { type: Number, default: 2.6 },
})

const svg = ref(null)
const error = ref('')

/**
 * mm 를 px 로 바꾼다.
 *
 * JsBarcode 는 px 로만 그린다. CSS 의 1mm 는 96/25.4 px 이므로 그 비율로
 * 넘기고, 완성된 SVG 의 width/height 를 다시 mm 로 바꿔 준다. 그러면
 * 인쇄할 때 실제 크기가 맞는다 — 라벨은 실물 크기가 틀리면 쓸모가 없다.
 */
const PX_PER_MM = 96 / 25.4

function draw() {
  if (!svg.value) return
  error.value = ''
  try {
    JsBarcode(svg.value, props.value, {
      format: props.format,
      width: props.moduleWidth * PX_PER_MM,
      height: props.height * PX_PER_MM,
      displayValue: props.showText,
      fontSize: props.fontSize * PX_PER_MM,
      textMargin: 0.5 * PX_PER_MM,
      // 여백(quiet zone). 가는 바의 10배 이상이어야 스캐너가 시작점을
      // 찾는다. 라벨을 빽빽하게 붙이다 여기가 먹히는 것이 가장 흔한
      // 실패 원인이다.
      margin: 0,
      marginLeft: props.moduleWidth * 10 * PX_PER_MM,
      marginRight: props.moduleWidth * 10 * PX_PER_MM,
      background: 'transparent',
      lineColor: '#000000',
      valid: (ok) => {
        if (!ok) error.value = `${props.format} 로 만들 수 없는 값입니다.`
      },
    })
    // px 로 그려진 크기를 mm 로 바꿔 실물 크기를 맞춘다.
    //
    // parseFloat 를 쓴다 — JsBarcode 는 width="319.01px" 처럼 단위를 붙여
    // 넣어서 Number() 로는 NaN 이 된다. 그러면 변환이 조용히 건너뛰어지고
    // 화면에서는 멀쩡해 보이는데 인쇄하면 크기가 어긋난다.
    const w = parseFloat(svg.value.getAttribute('width'))
    const h = parseFloat(svg.value.getAttribute('height'))
    if (w && h) {
      svg.value.setAttribute('viewBox', `0 0 ${w} ${h}`)
      svg.value.setAttribute('width', `${(w / PX_PER_MM).toFixed(2)}mm`)
      svg.value.setAttribute('height', `${(h / PX_PER_MM).toFixed(2)}mm`)
    }
  } catch (e) {
    error.value = e.message
  }
}

onMounted(draw)
watch(
  () => [props.value, props.format, props.moduleWidth, props.height, props.showText],
  draw,
)

/** 값이 비면 그릴 것이 없다 */
const empty = computed(() => !props.value)
</script>

<template>
  <span v-if="empty" class="ph">값 없음</span>
  <span v-else-if="error" class="ph err" :title="error">{{ error }}</span>
  <svg v-show="!error" ref="svg" class="bc"></svg>
</template>

<style scoped>
.bc {
  display: block;
}
.ph {
  display: inline-block;
  font-size: 10px;
  color: var(--fg-dim, #6b7280);
}
.err {
  color: var(--c-red, #dc2626);
}
</style>
