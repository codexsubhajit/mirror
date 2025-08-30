package com.example.modernandroidui.data

import androidx.room.*

@Dao
interface FaceMapDao {
    @Query("SELECT * FROM face_map WHERE faceId = :faceId")
    suspend fun getByFaceId(faceId: Long): FaceMapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(faceMap: FaceMapEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(faceMaps: List<FaceMapEntity>)

    @Query("DELETE FROM face_map")
    suspend fun clearAll()

    @Query("SELECT * FROM face_map")
    fun getAllFaceMapsSync(): List<FaceMapEntity>
}
