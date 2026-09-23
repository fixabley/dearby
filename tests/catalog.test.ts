import {test} from 'node:test';
import assert from 'node:assert/strict';
import {programs} from '../src/features/catalog/data';
import {emptyFilters as f,searchPrograms,suggestions,activityMatches,recruitment} from '../src/features/catalog/model';
test('unique programs, direction OR and AND',()=>{
 assert.equal(searchPrograms(programs,f).length,12);
 assert.ok(searchPrograms(programs,{...f,roles:['iOS','백엔드']}).length>searchPrograms(programs,{...f,roles:['iOS','백엔드'],allRoles:true}).length);
});
test('same notice prevents invented cross-role matches and past evidence',()=>{
 const p=programs[3];
 assert.equal(searchPrograms([p],{...f,roles:['iOS'],openOnly:true}).length,0);
 assert.equal(recruitment(p,{...f,roles:['iOS']}),'선택 직무 종료 · 다른 직무 모집 중');
 assert.equal(searchPrograms([programs[5]],{...f,experiences:['mentor']}).length,0);
});
test('activity attribute conjunction and parent inclusion',()=>{
 assert.ok(activityMatches({action:'제작',target:'앱',method:'팀협업'},{action:'제작'}));
 assert.ok(!activityMatches({action:'제작',target:'앱',method:'개인'},{action:'제작',target:'앱',method:'팀협업'}));
 assert.ok(searchPrograms(programs,{...f,experiences:['networking']}).length>=searchPrograms(programs,{...f,experiences:['mentor']}).length);
});
test('zero results offer only nonempty proper subsets under original logic',()=>{
 const filters={...f,roles:['백엔드'],experiences:['app']};
 assert.equal(searchPrograms(programs,filters).length,0);
 const options=suggestions(programs,filters); assert.ok(options.length);
 for(const s of options) {assert.ok(s.count>0);assert.equal(s.count,searchPrograms(programs,s.filters).length);assert.ok(s.removed.length);}
});
test('matching open roles outrank other-role recruitment',()=>{
 const result=searchPrograms(programs,{...f,roles:['iOS']});
 assert.notEqual(result[0].id,'app-club');
});
