/**
 * 제품 카탈로그 스토어 — 분류 · 브랜드.
 *
 * 둘을 함께 두는 이유는 제품 화면과 SKU 화면이 모두 이 둘을 드롭다운으로
 * 쓰기 때문이다. 화면마다 따로 읽으면 한쪽에서 만든 분류가 다른 쪽에는 없는
 * 상태가 되어 저장할 때마다 서버에 거부당한다.
 *
 * 제품과 SKU 는 여기에 두지 않는다. 수천 건이라 전체를 메모리에 들고 있을 수
 * 없고, 각 화면이 직접 페이징해서 읽는다. SKU 화면의 제품 드롭다운만 예외로
 * 필요한데, 그건 제품을 고를 때 검색으로 좁히는 편이 목록 전체를 받는 것보다
 * 낫다 — 지금은 제품 수가 적어 전체를 받되, 늘어나면 그때 바꾼다.
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as categoryApi from '@/api/category.js'
import * as brandApi from '@/api/brand.js'
import * as productApi from '@/api/product.js'

export const useCatalogStore = defineStore('catalog', () => {
  const categories = ref([])
  const brands = ref([])
  const products = ref([])

  const loading = ref(false)
  /** 조회 권한이 없어 목록을 받지 못한 경우의 사유 (종류별) */
  const denyReason = ref({ categories: '', brands: '', products: '' })
  const loaded = ref({ categories: false, brands: false, products: false })

  /**
   * 권한이 없어도 화면 전체를 실패로 만들지 않는다.
   *
   * 예를 들어 입고 작업자는 제품 · SKU 는 읽지만 브랜드는 못 읽는다.
   * 그때 브랜드 목록만 비워 두고 나머지는 정상으로 보여야 한다.
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

  async function loadCategories(force = false) {
    if (loaded.value.categories && !force) return categories.value
    categories.value = await loadKind('categories', () => categoryApi.list({ size: 0 }))
    return categories.value
  }

  async function loadBrands(force = false) {
    if (loaded.value.brands && !force) return brands.value
    brands.value = await loadKind('brands', () => brandApi.list({ size: 0 }))
    return brands.value
  }

  async function loadProducts(force = false) {
    if (loaded.value.products && !force) return products.value
    products.value = await loadKind('products', () => productApi.list({ size: 0 }))
    return products.value
  }

  /* ── 드롭다운용 목록 ────────────────────────────────────────── */

  /**
   * 전체 분류. 경로(의류 > 상의 > 티셔츠)를 라벨로 쓴다.
   * 분류명만으로는 어느 갈래인지 알 수 없기 때문이다.
   */
  const categoryOptions = computed(() =>
    categories.value.map((c) => ({
      value: c.categoryId,
      label: c.pathName ?? c.categoryName,
      disabled: c.useYn !== 'Y',
    })),
  )

  /** 제품을 붙일 수 있는 분류 — 소분류만. 이유는 ProductService 참고. */
  const leafCategoryOptions = computed(() =>
    categories.value
      .filter((c) => c.levelNo === 3)
      .map((c) => ({
        value: c.categoryId,
        label: c.pathName ?? c.categoryName,
        disabled: c.useYn !== 'Y',
      })),
  )

  /** 상위로 고를 수 있는 분류 — 한 단계 위만 */
  const parentCategoryOptions = (levelNo, exceptId) =>
    categories.value
      .filter((c) => c.levelNo === Number(levelNo) - 1 && c.categoryId !== exceptId)
      .map((c) => ({
        value: c.categoryId,
        label: c.pathName ?? c.categoryName,
        disabled: c.useYn !== 'Y',
      }))

  const brandOptions = computed(() =>
    brands.value.map((b) => ({
      value: b.brandId,
      label: `${b.brandName} (${b.brandId})`,
      disabled: b.useYn !== 'Y',
    })),
  )

  const productOptions = computed(() =>
    products.value.map((p) => ({
      value: p.productId,
      label: `${p.productName} (${p.productId})`,
      disabled: p.useYn !== 'Y',
    })),
  )

  const categoryPathOf = (categoryId) =>
    categories.value.find((c) => c.categoryId === categoryId)?.pathName ?? categoryId

  const productNameOf = (productId) =>
    products.value.find((p) => p.productId === productId)?.productName ?? productId

  return {
    categories,
    brands,
    products,
    loading,
    loaded,
    denyReason,
    loadCategories,
    loadBrands,
    loadProducts,
    categoryOptions,
    leafCategoryOptions,
    parentCategoryOptions,
    brandOptions,
    productOptions,
    categoryPathOf,
    productNameOf,
  }
})
