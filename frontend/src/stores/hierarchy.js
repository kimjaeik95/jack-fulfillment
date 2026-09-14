/**
 * 거점 계층 스토어 — 회사 · 플랜트 · 창고.
 *
 * 한 스토어에 셋을 함께 두는 이유는 화면들이 서로의 목록을 필요로 하기
 * 때문이다. 조직 화면은 회사 목록을, 플랜트 화면은 조직 목록을, 창고 화면은
 * 플랜트 목록을, 빈 화면은 플랜트와 창고 목록을 드롭다운으로 쓴다.
 * 화면마다 따로 읽으면 한쪽에서 만든 값이 다른 쪽에는 없는 상태가 되어
 * 저장할 때마다 서버에 거부당한다.
 *
 * 빈은 여기에 두지 않는다. 수백~수천 건이라 전체를 메모리에 들고 있을
 * 수 없고, 다른 화면의 드롭다운이 되지도 않는다 — 빈 화면이 직접
 * 페이징해서 읽는다.
 *
 * 조직은 기존 org 스토어가 이미 들고 있으므로 중복하지 않는다.
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as companyApi from '@/api/company.js'
import * as plantApi from '@/api/plant.js'
import * as warehouseApi from '@/api/warehouse.js'

export const useHierarchyStore = defineStore('hierarchy', () => {
  const companies = ref([])
  const plants = ref([])
  const warehouses = ref([])

  const loading = ref(false)
  /** 조회 권한이 없어 목록을 받지 못한 경우의 사유 (종류별) */
  const denyReason = ref({ companies: '', plants: '', warehouses: '' })
  const loaded = ref({ companies: false, plants: false, warehouses: false })

  /**
   * 권한이 없어도 화면 전체를 실패로 만들지 않는다.
   *
   * 예를 들어 센터 관리자는 플랜트 · 창고는 읽을 수 있지만 회사는 못 읽는다.
   * 그때 회사 목록만 비워 두고 나머지는 정상으로 보여야 한다.
   */
  async function loadKind(kind, fetcher) {
    loading.value = true
    try {
      const page = await fetcher()
      denyReason.value = { ...denyReason.value, [kind]: '' }
      return page.rows
    } catch (e) {
      denyReason.value = { ...denyReason.value, [kind]: e.message }
      return []
    } finally {
      loading.value = false
      loaded.value = { ...loaded.value, [kind]: true }
    }
  }

  async function loadCompanies(force = false) {
    if (loaded.value.companies && !force) return companies.value
    companies.value = await loadKind('companies', () => companyApi.list({ size: 0 }))
    return companies.value
  }

  async function loadPlants(force = false) {
    if (loaded.value.plants && !force) return plants.value
    plants.value = await loadKind('plants', () => plantApi.list({ size: 0 }))
    return plants.value
  }

  async function loadWarehouses(force = false) {
    if (loaded.value.warehouses && !force) return warehouses.value
    warehouses.value = await loadKind('warehouses', () => warehouseApi.list({ size: 0 }))
    return warehouses.value
  }

  /** 계층 전체. 한 화면이 상위 · 하위를 함께 쓸 때 한 번에 부른다. */
  async function loadAll(force = false) {
    await Promise.all([loadCompanies(force), loadPlants(force), loadWarehouses(force)])
  }

  /* ── 드롭다운용 목록 ────────────────────────────────────────── */

  const companyOptions = computed(() =>
    companies.value.map((c) => ({
      value: c.companyId,
      label: `${c.companyName} (${c.companyId})`,
      disabled: c.useYn !== 'Y',
    })),
  )

  const plantOptions = computed(() =>
    plants.value.map((p) => ({
      value: p.plantId,
      label: `${p.plantName} (${p.plantId})`,
      disabled: p.useYn !== 'Y',
    })),
  )

  /**
   * 한 플랜트의 창고만.
   *
   * 창고코드는 플랜트 안에서만 유일하므로 플랜트를 고르지 않은 상태의
   * 창고 드롭다운은 의미가 없다 — GD 가 여러 개 보인다.
   */
  const warehouseOptionsOf = (plantId) =>
    warehouses.value
      .filter((w) => w.plantId === plantId)
      .map((w) => ({
        value: w.warehouseId,
        label: `${w.warehouseName} (${w.warehouseId})`,
        disabled: w.useYn !== 'Y',
      }))

  const companyNameOf = (companyId) =>
    companies.value.find((c) => c.companyId === companyId)?.companyName ?? companyId

  const plantNameOf = (plantId) =>
    plants.value.find((p) => p.plantId === plantId)?.plantName ?? plantId

  /** 창고유형. 빈 관리 화면이 "유형이 어긋나는가"를 미리 보여주는 데 쓴다. */
  const warehouseTypeOf = (plantId, warehouseId) =>
    warehouses.value.find((w) => w.plantId === plantId && w.warehouseId === warehouseId)
      ?.warehouseType ?? null

  /**
   * 다른 화면이 바꾼 종류를 낡음으로 표시한다. 종류를 주지 않으면 전부.
   * 다음에 그 목록을 쓰는 화면이 열릴 때 다시 읽는다.
   */
  function invalidate(...kinds) {
    const next = { ...loaded.value }
    for (const k of kinds.length ? kinds : Object.keys(next)) next[k] = false
    loaded.value = next
  }

  return {
    companies,
    plants,
    warehouses,
    loading,
    loaded,
    denyReason,
    loadCompanies,
    loadPlants,
    loadWarehouses,
    loadAll,
    invalidate,
    companyOptions,
    plantOptions,
    warehouseOptionsOf,
    companyNameOf,
    plantNameOf,
    warehouseTypeOf,
  }
})
