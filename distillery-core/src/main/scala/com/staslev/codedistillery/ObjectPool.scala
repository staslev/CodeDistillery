/*
 * Copyright 2019 Stas Levin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.staslev.codedistillery
import java.util.concurrent.atomic.AtomicReference

trait ObjectPool[KeyT, ValueT] {

  private val instances: AtomicReference[Map[KeyT, ValueT]] =
    new AtomicReference(Map())

  def getOrCreate(key: KeyT, factory: KeyT => ValueT): ValueT = {
    var stop = false
    var spins = 0
    while (!stop) {
      val map = instances.get()
      if (!map.contains(key)) {
        if (instances.compareAndSet(map, map.updated(key, factory.apply(key)))) {
          stop = true
        } else {
          spins = spins + 1
        }
      } else {
        stop = true
      }
    }
    instances.get().apply(key)
  }
}
